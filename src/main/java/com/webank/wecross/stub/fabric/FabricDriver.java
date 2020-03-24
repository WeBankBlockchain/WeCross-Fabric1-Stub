package com.webank.wecross.stub.fabric;

import static com.webank.wecross.utils.FabricUtils.bytesToLong;
import static com.webank.wecross.utils.FabricUtils.longToBytes;

import com.google.protobuf.ByteString;
import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.BlockHeader;
import com.webank.wecross.stub.BlockHeaderManager;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.Request;
import com.webank.wecross.stub.Response;
import com.webank.wecross.stub.TransactionContext;
import com.webank.wecross.stub.TransactionRequest;
import com.webank.wecross.stub.TransactionResponse;
import java.nio.charset.Charset;
import org.hyperledger.fabric.protos.common.Common;
import org.hyperledger.fabric.protos.peer.FabricProposalResponse;
import org.hyperledger.fabric.protos.peer.FabricTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FabricDriver implements Driver {
    private Logger logger = LoggerFactory.getLogger(FabricDriver.class);

    public byte[] encodeTransactionRequest(TransactionContext<TransactionRequest> request) {
        try {
            return EndorserRequestFactory.encode(request);
        } catch (Exception e) {
            logger.error("encodeTransactionRequest error: " + e);
            return null;
        }
    }

    @Override
    public TransactionContext<TransactionRequest> decodeTransactionRequest(byte[] data) {
        try {
            return EndorserRequestFactory.decode(data);
        } catch (Exception e) {
            logger.error("decodeTransactionRequest error: " + e);
            return null;
        }
    }

    public byte[] encodeTransactionResponse(TransactionResponse response) {

        switch (response.getResult().length) {
            case 0:
                return new byte[] {};
            case 1:
                String result = response.getResult()[0];
                ByteString payload = ByteString.copyFrom(result, Charset.forName("UTF-8"));
                return payload.toByteArray();
            default:
                logger.error(
                        "encodeTransactionResponse error: Illegal result size: "
                                + response.getResult().length);
                return null;
        }
    }

    public TransactionResponse decodeTransactionResponse(byte[] data) {
        // Fabric only has 1 return object
        ByteString payload = ByteString.copyFrom(data);
        String[] result = new String[] {payload.toStringUtf8()};

        TransactionResponse response = new TransactionResponse();
        response.setResult(result);
        return response;
    }

    @Override
    public boolean isTransaction(Request request) {
        switch (request.getType()) {
            case FabricType.ConnectionMessage.FABRIC_CALL:
            case FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ENDORSER:
                return true;
            default:
                return false;
        }
    }

    @Override
    public BlockHeader decodeBlockHeader(byte[] data) {
        try {
            FabricBlock block = FabricBlock.encode(data);
            return block.dumpWeCrossHeader();
        } catch (Exception e) {
            logger.error("decodeBlockHeader error: " + e);
            return null;
        }
    }

    @Override
    public TransactionResponse call(
            TransactionContext<TransactionRequest> request, Connection connection) {
        TransactionResponse response = new TransactionResponse();
        try {
            // check
            checkRequest(request);

            Request endorserRequest = EndorserRequestFactory.build(request);
            endorserRequest.setType(FabricType.ConnectionMessage.FABRIC_CALL);
            endorserRequest.setResourceInfo(request.getResourceInfo());

            Response connectionResponse = connection.send(endorserRequest);

            if (connectionResponse.getErrorCode() == FabricType.ResponseStatus.SUCCESS) {
                response = decodeTransactionResponse(connectionResponse.getData());
                response.setHash(getTxIDFromEnvelopeBytes(endorserRequest.getData()));
            }
            response.setErrorCode(connectionResponse.getErrorCode());
            response.setErrorMessage(connectionResponse.getErrorMessage());

        } catch (Exception e) {
            String errorMessage = "Fabric driver call exception: " + e.getMessage();
            logger.error(errorMessage);

            response.setErrorCode(FabricType.ResponseStatus.INTERNAL_ERROR);
            response.setErrorMessage(errorMessage);
        }
        return response;
    }

    @Override
    public TransactionResponse sendTransaction(
            TransactionContext<TransactionRequest> request, Connection connection) {
        TransactionResponse response = new TransactionResponse();
        try {
            // check
            checkRequest(request);

            // Send to endorser
            Request endorserRequest = EndorserRequestFactory.build(request);
            endorserRequest.setType(FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ENDORSER);
            endorserRequest.setResourceInfo(request.getResourceInfo());

            Response endorserResponse = connection.send(endorserRequest);

            if (endorserResponse.getErrorCode() != FabricType.ResponseStatus.SUCCESS) {
                response.setErrorCode(endorserResponse.getErrorCode());
                response.setErrorMessage(endorserResponse.getErrorMessage());
            } else {
                // Send to orderer
                byte[] ordererPayloadToSign = endorserResponse.getData();
                Request ordererRequest =
                        OrdererRequestFactory.build(request.getAccount(), ordererPayloadToSign);
                ordererRequest.setType(FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ORDERER);
                ordererRequest.setResourceInfo(request.getResourceInfo());

                Response ordererResponse = connection.send(ordererRequest);

                if (ordererResponse.getErrorCode() != FabricType.ResponseStatus.SUCCESS) {

                    response.setErrorCode(ordererResponse.getErrorCode());
                    response.setErrorMessage(ordererResponse.getErrorMessage());

                } else {
                    // Success, verify transaction
                    String txID = getTxIDFromEnvelopeBytes(endorserRequest.getData());
                    long txBlockNumber = bytesToLong(ordererResponse.getData());

                    if (!hasTransactionOnChain(
                            txID, txBlockNumber, request.getBlockHeaderManager())) {
                        response.setErrorCode(
                                FabricType.ResponseStatus.FABRIC_TX_ONCHAIN_VERIFY_FAIED);
                        response.setErrorMessage(
                                "Verify failed. Tx("
                                        + txID
                                        + ") is not on chain( "
                                        + txBlockNumber
                                        + ")");
                    } else {
                        response =
                                decodeTransactionResponse(getResponsePayload(ordererPayloadToSign));
                        response.setHash(txID);
                        response.setErrorCode(FabricType.ResponseStatus.SUCCESS);
                        response.setErrorMessage("Success");
                    }
                }

                // Verify transaction has on chain
                // private ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();
            }
        } catch (Exception e) {
            String errorMessage = "Fabric driver call exception: " + e.getMessage();
            logger.error(errorMessage);

            response.setErrorCode(FabricType.ResponseStatus.INTERNAL_ERROR);
            response.setErrorMessage(errorMessage);
        }
        return response;
    }

    @Override
    public long getBlockNumber(Connection connection) {
        try {
            byte[] blockBytes = getBlockHeader(-1, connection);
            Common.Block block = Common.Block.parseFrom(blockBytes);
            return block.getHeader().getNumber();
        } catch (Exception e) {
            logger.error("Get block header failed: " + e);
            return -1;
        }
    }

    public long getBlockNumber2(Connection connection) {
        // Test failed
        Request request = new Request();
        request.setType(FabricType.ConnectionMessage.FABRIC_GET_BLOCK_NUMBER);

        Response response = connection.send(request);
        if (response.getErrorCode() == FabricType.ResponseStatus.SUCCESS) {

            return bytesToLong(response.getData());
        } else {
            logger.error("Get block header failed: " + response.getErrorMessage());
            return -1;
        }
    }

    @Override
    public byte[] getBlockHeader(long number, Connection connection) {

        byte[] numberBytes = longToBytes(number);

        Request request = new Request();
        request.setType(FabricType.ConnectionMessage.FABRIC_GET_BLOCK_HEADER);
        request.setData(numberBytes);

        Response response = connection.send(request);

        if (response.getErrorCode() == FabricType.ResponseStatus.SUCCESS) {
            return response.getData();
        } else {
            logger.error("Get block header failed: " + response.getErrorMessage());
            return null;
        }
    }

    private boolean hasTransactionOnChain(
            String txID, long blockNumber, BlockHeaderManager blockHeaderManager) throws Exception {
        logger.debug("To verify transaction, waiting fabric block syncing ...");
        byte[] blockBytes =
                blockHeaderManager.getBlock(blockNumber); // waiting until receiving the block

        logger.debug("Receive block, verify transaction ...");
        FabricBlock block = FabricBlock.encode(blockBytes);
        boolean verifyResult = block.hasTransaction(txID);

        logger.debug("Tx(block: " + blockNumber + "): " + txID + " verify: " + verifyResult);

        return verifyResult;
    }

    private byte[] getResponsePayload(byte[] payloadBytes) throws Exception {
        Common.Payload payload = Common.Payload.parseFrom(payloadBytes);
        FabricTransaction.Transaction tx =
                FabricTransaction.Transaction.parseFrom(payload.getData());
        FabricTransaction.TransactionAction action = tx.getActions(0);
        FabricTransaction.ChaincodeActionPayload chaincodeActionPayload =
                FabricTransaction.ChaincodeActionPayload.parseFrom(action.getPayload());
        FabricTransaction.ChaincodeEndorsedAction chaincodeEndorsedAction =
                chaincodeActionPayload.getAction();

        FabricProposalResponse.ProposalResponsePayload proposalResponsePayload =
                FabricProposalResponse.ProposalResponsePayload.parseFrom(
                        chaincodeEndorsedAction.getProposalResponsePayload());
        org.hyperledger.fabric.protos.peer.FabricProposal.ChaincodeAction chaincodeAction =
                org.hyperledger.fabric.protos.peer.FabricProposal.ChaincodeAction.parseFrom(
                        proposalResponsePayload.getExtension());
        byte[] ret = chaincodeAction.getResponse().getPayload().toByteArray();

        return ret;
    }

    private String getTxIDFromEnvelopeBytes(byte[] envelopeBytes) throws Exception {
        Common.Envelope envelope = Common.Envelope.parseFrom(envelopeBytes);

        Common.Payload payload = Common.Payload.parseFrom(envelope.getPayload().toByteArray());

        Common.ChannelHeader channelHeader =
                Common.ChannelHeader.parseFrom(payload.getHeader().getChannelHeader());
        return channelHeader.getTxId();
    }

    private void checkRequest(TransactionContext<TransactionRequest> request) throws Exception {
        if (request.getAccount() == null) {
            throw new Exception("Unknown account");
        }

        if (request.getBlockHeaderManager() == null) {
            throw new Exception("blockHeaderManager is null");
        }

        if (request.getResourceInfo() == null) {
            throw new Exception("resourceInfo is null");
        }

        if (request.getData() == null) {
            throw new Exception("TransactionRequest is null");
        }
    }
}
