package com.webank.wecross.stub.fabric;

import com.google.protobuf.ByteString;
import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.BlockHeader;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.Request;
import com.webank.wecross.stub.Response;
import com.webank.wecross.stub.TransactionContext;
import com.webank.wecross.stub.TransactionRequest;
import com.webank.wecross.stub.TransactionResponse;
import org.hyperledger.fabric.protos.common.Common;
import org.hyperledger.fabric.protos.peer.FabricProposalResponse;
import org.hyperledger.fabric.protos.peer.FabricTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FabricDriver implements Driver {
    private Logger logger = LoggerFactory.getLogger(FabricDriver.class);

    @Override
    public byte[] encodeTransactionRequest(TransactionContext<TransactionRequest> request) {
        return new byte[0];
    }

    @Override
    public TransactionContext<TransactionRequest> decodeTransactionRequest(byte[] data) {
        return null;
    }

    @Override
    public byte[] encodeTransactionResponse(TransactionResponse response) {
        return new byte[0];
    }

    @Override
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
        return null;
    }

    @Override
    public TransactionResponse call(
            TransactionContext<TransactionRequest> request, Connection connection) {
        TransactionResponse response = new TransactionResponse();
        try {
            Request endorserRequest = EndorserRequestFactory.build(request);
            endorserRequest.setType(FabricType.ConnectionMessage.FABRIC_CALL);
            endorserRequest.setResourceInfo(request.getResourceInfo());

            Response connectionResponse = connection.send(endorserRequest);

            if (connectionResponse.getErrorCode() == FabricType.ResponseStatus.SUCCESS) {
                response = decodeTransactionResponse(connectionResponse.getData());
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
                if (ordererResponse.getErrorCode() == FabricType.ResponseStatus.SUCCESS) {
                    response = decodeTransactionResponse(getResponsePayload(ordererPayloadToSign));
                }
                response.setErrorCode(ordererResponse.getErrorCode());
                response.setErrorMessage(ordererResponse.getErrorMessage());
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
        return 0;
    }

    @Override
    public byte[] getBlockHeader(long number, Connection connection) {
        return new byte[0];
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
}
