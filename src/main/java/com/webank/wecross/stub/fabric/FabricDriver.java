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
import com.webank.wecross.stub.VerifiedTransaction;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
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
            CompletableFuture<TransactionResponse> future = new CompletableFuture<>();

            asyncCall(
                    request,
                    connection,
                    new Driver.Callback() {
                        @Override
                        public void onTransactionResponse(TransactionResponse transactionResponse) {
                            future.complete(transactionResponse);
                        }
                    });
            return future.get();

        } catch (Exception e) {
            String errorMessage = "Fabric driver call exception: " + e;
            logger.error(errorMessage);

            response.setErrorCode(FabricType.TransactionResponseStatus.INTERNAL_ERROR);
            response.setErrorMessage(errorMessage);
        }
        return response;
    }

    @Override
    public void asyncCall(
            TransactionContext<TransactionRequest> request,
            Connection connection,
            Driver.Callback callback) {

        try {
            // check
            checkRequest(request);

            Request endorserRequest = EndorserRequestFactory.build(request);
            endorserRequest.setType(FabricType.ConnectionMessage.FABRIC_CALL);
            endorserRequest.setResourceInfo(request.getResourceInfo());

            connection.asyncSend(
                    endorserRequest,
                    new Connection.Callback() {
                        @Override
                        public void onResponse(Response connectionResponse) {
                            TransactionResponse response = new TransactionResponse();
                            try {
                                if (connectionResponse.getErrorCode()
                                        == FabricType.TransactionResponseStatus.SUCCESS) {
                                    response =
                                            decodeTransactionResponse(connectionResponse.getData());
                                    response.setHash(
                                            EndorserRequestFactory.getTxIDFromEnvelopeBytes(
                                                    endorserRequest.getData()));
                                }
                                response.setErrorCode(connectionResponse.getErrorCode());
                                response.setErrorMessage(connectionResponse.getErrorMessage());
                            } catch (Exception e) {
                                String errorMessage =
                                        "Fabric driver call onResponse exception: " + e;
                                logger.error(errorMessage);
                                response.setErrorCode(
                                        FabricType.TransactionResponseStatus.INTERNAL_ERROR);
                                response.setErrorMessage(errorMessage);
                            }
                            callback.onTransactionResponse(response);
                        }
                    });

        } catch (Exception e) {
            String errorMessage = "Fabric driver call exception: " + e;
            logger.error(errorMessage);
            TransactionResponse response = new TransactionResponse();
            response.setErrorCode(FabricType.TransactionResponseStatus.INTERNAL_ERROR);
            response.setErrorMessage(errorMessage);
            callback.onTransactionResponse(response);
        }
    }

    @Override
    public TransactionResponse sendTransaction(
            TransactionContext<TransactionRequest> request, Connection connection) {
        TransactionResponse response = new TransactionResponse();
        try {
            CompletableFuture<TransactionResponse> future = new CompletableFuture<>();
            asyncSendTransaction(
                    request,
                    connection,
                    new Driver.Callback() {
                        @Override
                        public void onTransactionResponse(TransactionResponse transactionResponse) {
                            future.complete(transactionResponse);
                        }
                    });

            response = future.get();

        } catch (Exception e) {
            String errorMessage = "Fabric driver call exception: " + e;
            logger.error(errorMessage);

            response.setErrorCode(FabricType.TransactionResponseStatus.INTERNAL_ERROR);
            response.setErrorMessage(errorMessage);
        }
        return response;
    }

    @Override
    public void asyncSendTransaction(
            TransactionContext<TransactionRequest> request,
            Connection connection,
            Driver.Callback callback) {
        try {
            // check
            checkRequest(request);

            // Send to endorser
            Request endorserRequest = EndorserRequestFactory.build(request);
            endorserRequest.setType(FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ENDORSER);
            endorserRequest.setResourceInfo(request.getResourceInfo());

            connection.asyncSend(
                    endorserRequest,
                    new Connection.Callback() {
                        @Override
                        public void onResponse(Response endorserResponse) {
                            asyncSendTransactionHandleEndorserResponse(
                                    request,
                                    endorserRequest,
                                    endorserResponse,
                                    connection,
                                    callback);
                        }
                    });

        } catch (Exception e) {
            String errorMessage = "Fabric driver call exception: " + e;
            logger.error(errorMessage);
            TransactionResponse response = new TransactionResponse();
            response.setErrorCode(FabricType.TransactionResponseStatus.INTERNAL_ERROR);
            response.setErrorMessage(errorMessage);
            callback.onTransactionResponse(response);
        }
    }

    private void asyncSendTransactionHandleEndorserResponse(
            TransactionContext<TransactionRequest> request,
            Request endorserRequest,
            Response endorserResponse,
            Connection connection,
            Driver.Callback callback) {
        if (endorserResponse.getErrorCode() != FabricType.TransactionResponseStatus.SUCCESS) {
            TransactionResponse response = new TransactionResponse();
            response.setErrorCode(endorserResponse.getErrorCode());
            response.setErrorMessage(endorserResponse.getErrorMessage());
            callback.onTransactionResponse(response);
            return;
        } else {
            // Send to orderer
            try {
                byte[] ordererPayloadToSign = endorserResponse.getData();
                Request ordererRequest =
                        OrdererRequestFactory.build(request.getAccount(), ordererPayloadToSign);
                ordererRequest.setType(FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ORDERER);
                ordererRequest.setResourceInfo(request.getResourceInfo());

                connection.asyncSend(
                        ordererRequest,
                        new Connection.Callback() {
                            @Override
                            public void onResponse(Response ordererResponse) {
                                asyncSendTransactionHandleOrdererResponse(
                                        request,
                                        endorserRequest,
                                        ordererPayloadToSign,
                                        ordererResponse,
                                        callback);
                            }
                        });

            } catch (Exception e) {
                String errorMessage = "Fabric driver call orderer exception: " + e;
                logger.error(errorMessage);
                TransactionResponse response = new TransactionResponse();
                response.setErrorCode(FabricType.TransactionResponseStatus.INTERNAL_ERROR);
                response.setErrorMessage(errorMessage);
                callback.onTransactionResponse(response);
            }
        }
    }

    private void asyncSendTransactionHandleOrdererResponse(
            TransactionContext<TransactionRequest> request,
            Request endorserRequest,
            byte[] ordererPayloadToSign,
            Response ordererResponse,
            Driver.Callback callback) {
        try {
            TransactionResponse response = new TransactionResponse();
            if (ordererResponse.getErrorCode() != FabricType.TransactionResponseStatus.SUCCESS) {

                response.setErrorCode(ordererResponse.getErrorCode());
                response.setErrorMessage(ordererResponse.getErrorMessage());

            } else {
                // Success, verify transaction
                String txID =
                        EndorserRequestFactory.getTxIDFromEnvelopeBytes(endorserRequest.getData());
                long txBlockNumber = bytesToLong(ordererResponse.getData());

                if (!hasTransactionOnChain(txID, txBlockNumber, request.getBlockHeaderManager())) {
                    response.setErrorCode(
                            FabricType.TransactionResponseStatus.FABRIC_TX_ONCHAIN_VERIFY_FAIED);
                    response.setErrorMessage(
                            "Verify failed. Tx("
                                    + txID
                                    + ") is invalid or not on block("
                                    + txBlockNumber
                                    + ")");
                } else {
                    response =
                            decodeTransactionResponse(
                                    FabricTransaction.buildFromPayloadBytes(ordererPayloadToSign)
                                            .getOutputBytes());
                    response.setHash(txID);
                    response.setBlockNumber(txBlockNumber);
                    response.setErrorCode(FabricType.TransactionResponseStatus.SUCCESS);
                    response.setErrorMessage("Success");
                }
            }
            callback.onTransactionResponse(response);
        } catch (Exception e) {
            String errorMessage = "Fabric driver call handle orderer response exception: " + e;
            logger.error(errorMessage);
            TransactionResponse response = new TransactionResponse();
            response.setErrorCode(FabricType.TransactionResponseStatus.INTERNAL_ERROR);
            response.setErrorMessage(errorMessage);
            callback.onTransactionResponse(response);
        }
    }

    @Override
    public long getBlockNumber(Connection connection) {
        // Test failed
        Request request = new Request();
        request.setType(FabricType.ConnectionMessage.FABRIC_GET_BLOCK_NUMBER);

        Response response = connection.send(request);
        if (response.getErrorCode() == FabricType.TransactionResponseStatus.SUCCESS) {
            long blockNumber = bytesToLong(response.getData());
            logger.debug("Get block number: " + blockNumber);
            return blockNumber;
        } else {
            logger.error("Get block number failed: " + response.getErrorMessage());
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

        if (response.getErrorCode() == FabricType.TransactionResponseStatus.SUCCESS) {
            return response.getData();
        } else {
            logger.error("Get block header failed: " + response.getErrorMessage());
            return null;
        }
    }

    @Override
    public VerifiedTransaction getVerifiedTransaction(
            String transactionHash,
            long blockNumber,
            BlockHeaderManager blockHeaderManager,
            Connection connection) {
        try {
            Request request = new Request();
            request.setType(FabricType.ConnectionMessage.FABRIC_GET_TRANSACTION);
            request.setData(transactionHash.getBytes(StandardCharsets.UTF_8));

            Response response = connection.send(request);

            if (response.getErrorCode() == FabricType.TransactionResponseStatus.SUCCESS) {

                // Generate Verified transaction
                FabricTransaction fabricTransaction =
                        FabricTransaction.buildFromEnvelopeBytes(response.getData());
                String txID = fabricTransaction.getTxID();
                String chaincodeName = fabricTransaction.getChaincodeName();

                if (!transactionHash.equals(txID)) {
                    throw new Exception(
                            "Request txHash: " + transactionHash + " but response: " + txID);
                }

                if (!hasTransactionOnChain(txID, blockNumber, blockHeaderManager)) {
                    throw new Exception(
                            "Verify failed. Tx("
                                    + txID
                                    + ") is invalid or not on block("
                                    + blockNumber
                                    + ")");
                }

                TransactionRequest transactionRequest = new TransactionRequest();
                transactionRequest.setMethod(fabricTransaction.getMethod());
                transactionRequest.setArgs(fabricTransaction.getArgs().toArray(new String[] {}));

                TransactionResponse transactionResponse =
                        decodeTransactionResponse(fabricTransaction.getOutputBytes());
                transactionResponse.setHash(txID);
                transactionResponse.setErrorCode(FabricType.TransactionResponseStatus.SUCCESS);
                transactionResponse.setBlockNumber(blockNumber);

                VerifiedTransaction verifiedTransaction =
                        new VerifiedTransaction(
                                blockNumber,
                                txID,
                                chaincodeName,
                                transactionRequest,
                                transactionResponse);

                return verifiedTransaction;
            } else {
                throw new Exception(response.getErrorMessage());
            }
        } catch (Exception e) {
            logger.error("Get transaction failed: " + e);
        }

        return null;
    }

    private boolean hasTransactionOnChain(
            String txID, long blockNumber, BlockHeaderManager blockHeaderManager) throws Exception {
        logger.debug("To verify transaction, waiting fabric block syncing ...");
        byte[] blockBytes =
                blockHeaderManager.getBlockHeader(blockNumber); // waiting until receiving the block

        logger.debug("Receive block, verify transaction ...");
        FabricBlock block = FabricBlock.encode(blockBytes);
        boolean verifyResult = block.hasTransaction(txID);

        logger.debug("Tx(block: " + blockNumber + "): " + txID + " verify: " + verifyResult);

        return verifyResult;
    }

    private void checkRequest(TransactionContext<TransactionRequest> request) throws Exception {
        if (request.getAccount() == null) {
            throw new Exception("Unknown account");
        }

        if (!request.getAccount().getType().equals(FabricType.Account.FABRIC_ACCOUNT)) {
            throw new Exception(
                    "Illegal account type for fabric call: " + request.getAccount().getType());
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

        if (request.getData().getArgs() == null) {
            // Fabric has no null args, just pass it as String[0]
            request.getData().setArgs(new String[0]);
        }
    }
}
