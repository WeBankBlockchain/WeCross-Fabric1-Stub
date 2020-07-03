package com.webank.wecross.stub.fabric;

import static com.webank.wecross.utils.FabricUtils.bytesToLong;
import static com.webank.wecross.utils.FabricUtils.longToBytes;

import com.google.protobuf.ByteString;
import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.Account;
import com.webank.wecross.stub.BlockHeader;
import com.webank.wecross.stub.BlockHeaderManager;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.Path;
import com.webank.wecross.stub.Request;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.Response;
import com.webank.wecross.stub.TransactionContext;
import com.webank.wecross.stub.TransactionException;
import com.webank.wecross.stub.TransactionRequest;
import com.webank.wecross.stub.TransactionResponse;
import com.webank.wecross.stub.VerifiedTransaction;
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstallCommand;
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstantiateCommand;
import com.webank.wecross.stub.fabric.proxy.ProxyChaincodeResource;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FabricDriver implements Driver {
    private Logger logger = LoggerFactory.getLogger(FabricDriver.class);

    public byte[] encodeTransactionRequest(TransactionContext<TransactionRequest> request) {
        try {
            byte[] data = EndorserRequestFactory.encode(request);

            TransactionParams transactionParams =
                    new TransactionParams(request.getData(), data, false);

            return transactionParams.toBytes();
        } catch (Exception e) {
            logger.error("encodeTransactionRequest error: " + e);
            return null;
        }
    }

    @Override
    public TransactionContext<TransactionRequest> decodeTransactionRequest(byte[] data) {
        try {
            TransactionParams transactionParams = TransactionParams.parseFrom(data);
            TransactionRequest plainRequest = transactionParams.getOriginTransactionRequest();

            TransactionContext<TransactionRequest> recoverContext =
                    EndorserRequestFactory.decode(transactionParams.getData());

            if (!transactionParams.isByProxy()) {
                // check the same
                TransactionRequest recoverRequest = recoverContext.getData();
                if (!recoverRequest.getMethod().equals(plainRequest.getMethod())
                        || !Arrays.equals(recoverRequest.getArgs(), plainRequest.getArgs())) {
                    throw new Exception(
                            "Illegal transaction request bytes, recover: "
                                    + recoverRequest
                                    + " plain: "
                                    + plainRequest);
                }

            } else {
                // TODO: Verify proxy transaction
            }

            TransactionContext<TransactionRequest> context =
                    new TransactionContext<>(
                            plainRequest,
                            recoverContext.getAccount(),
                            recoverContext.getPath(),
                            recoverContext.getResourceInfo(),
                            recoverContext.getBlockHeaderManager());

            return context;
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
            TransactionContext<TransactionRequest> request, Connection connection)
            throws TransactionException {
        TransactionResponse response = new TransactionResponse();

        CompletableFuture<TransactionResponse> future = new CompletableFuture<>();
        CompletableFuture<TransactionException> exceptionFuture = new CompletableFuture<>();

        asyncCall(
                request,
                connection,
                new Driver.Callback() {
                    @Override
                    public void onTransactionResponse(
                            TransactionException transactionException,
                            TransactionResponse transactionResponse) {
                        exceptionFuture.complete(transactionException);
                        future.complete(transactionResponse);
                    }
                });

        TransactionException transactionException;

        try {
            transactionException = exceptionFuture.get();
            response = future.get();
        } catch (Exception e) {
            throw TransactionException.Builder.newInternalException(
                    "Call: future get exception" + e);
        }

        if (!transactionException.isSuccess()) {
            throw transactionException;
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

            byte[] data = EndorserRequestFactory.buildProposalRequestBytes(request);
            TransactionParams transactionParams =
                    new TransactionParams(request.getData(), data, false);

            Request endorserRequest = new Request();
            endorserRequest.setData(transactionParams.toBytes());
            endorserRequest.setType(FabricType.ConnectionMessage.FABRIC_CALL);
            endorserRequest.setResourceInfo(request.getResourceInfo());

            connection.asyncSend(
                    endorserRequest,
                    new Connection.Callback() {
                        @Override
                        public void onResponse(Response connectionResponse) {
                            TransactionResponse response = new TransactionResponse();
                            TransactionException transactionException;
                            try {
                                if (connectionResponse.getErrorCode()
                                        == FabricType.TransactionResponseStatus.SUCCESS) {
                                    response =
                                            decodeTransactionResponse(connectionResponse.getData());
                                    response.setHash(
                                            EndorserRequestFactory.getTxIDFromEnvelopeBytes(data));
                                }
                                transactionException =
                                        new TransactionException(
                                                connectionResponse.getErrorCode(),
                                                connectionResponse.getErrorMessage());
                            } catch (Exception e) {
                                String errorMessage =
                                        "Fabric driver call onResponse exception: " + e;
                                logger.error(errorMessage);
                                transactionException =
                                        TransactionException.Builder.newInternalException(
                                                errorMessage);
                            }
                            callback.onTransactionResponse(transactionException, response);
                        }
                    });

        } catch (Exception e) {
            String errorMessage = "Fabric driver call exception: " + e;
            logger.error(errorMessage);
            TransactionResponse response = new TransactionResponse();
            TransactionException transactionException =
                    TransactionException.Builder.newInternalException(errorMessage);
            callback.onTransactionResponse(transactionException, response);
        }
    }

    @Override
    public void asyncCallByProxy(
            TransactionContext<TransactionRequest> request,
            Connection connection,
            Callback callback) {

        try {
            checkProxyRequest(request);

            TransactionContext<TransactionRequest> proxyRequest =
                    ProxyChaincodeResource.toProxyRequest(
                            request, ProxyChaincodeResource.MethodType.CALL);

            byte[] data = EndorserRequestFactory.buildProposalRequestBytes(proxyRequest);
            TransactionParams transactionParams =
                    new TransactionParams(request.getData(), data, true);

            Request endorserRequest = new Request();
            endorserRequest.setData(transactionParams.toBytes());
            endorserRequest.setType(FabricType.ConnectionMessage.FABRIC_CALL);
            endorserRequest.setResourceInfo(request.getResourceInfo());

            connection.asyncSend(
                    endorserRequest,
                    new Connection.Callback() {
                        @Override
                        public void onResponse(Response connectionResponse) {
                            TransactionResponse response = new TransactionResponse();
                            TransactionException transactionException;
                            try {
                                if (connectionResponse.getErrorCode()
                                        == FabricType.TransactionResponseStatus.SUCCESS) {
                                    response =
                                            decodeTransactionResponse(connectionResponse.getData());
                                    response.setHash(
                                            EndorserRequestFactory.getTxIDFromEnvelopeBytes(data));
                                }
                                transactionException =
                                        new TransactionException(
                                                connectionResponse.getErrorCode(),
                                                connectionResponse.getErrorMessage());
                            } catch (Exception e) {
                                String errorMessage =
                                        "Fabric driver callByProxy onResponse exception: " + e;
                                logger.error(errorMessage);
                                transactionException =
                                        TransactionException.Builder.newInternalException(
                                                errorMessage);
                            }
                            callback.onTransactionResponse(transactionException, response);
                        }
                    });

        } catch (Exception e) {
            callback.onTransactionResponse(
                    new TransactionException(
                            TransactionException.ErrorCode.INTERNAL_ERROR,
                            "asyncCallByProxy exception: " + e),
                    null);
        }
    }

    @Override
    public TransactionResponse sendTransaction(
            TransactionContext<TransactionRequest> request, Connection connection)
            throws TransactionException {

        TransactionResponse response = new TransactionResponse();

        CompletableFuture<TransactionResponse> future = new CompletableFuture<>();
        CompletableFuture<TransactionException> exceptionFuture = new CompletableFuture<>();

        asyncSendTransaction(
                request,
                connection,
                new Driver.Callback() {
                    @Override
                    public void onTransactionResponse(
                            TransactionException transactionException,
                            TransactionResponse transactionResponse) {
                        exceptionFuture.complete(transactionException);
                        future.complete(transactionResponse);
                    }
                });

        TransactionException transactionException;

        try {
            transactionException = exceptionFuture.get();
            response = future.get();
        } catch (Exception e) {
            throw TransactionException.Builder.newInternalException(
                    "Sendtransaction: future get exception" + e);
        }

        if (!transactionException.isSuccess()
                && !transactionException
                        .getErrorCode()
                        .equals(
                                FabricType.TransactionResponseStatus
                                        .FABRIC_EXECUTE_CHAINCODE_FAILED)) {
            throw transactionException;
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
            byte[] data = EndorserRequestFactory.buildProposalRequestBytes(request);
            TransactionParams transactionParams =
                    new TransactionParams(request.getData(), data, false);

            Request endorserRequest = new Request();
            endorserRequest.setData(transactionParams.toBytes());
            endorserRequest.setType(FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ENDORSER);
            endorserRequest.setResourceInfo(request.getResourceInfo());

            connection.asyncSend(
                    endorserRequest,
                    new Connection.Callback() {
                        @Override
                        public void onResponse(Response endorserResponse) {
                            asyncSendTransactionHandleEndorserResponse(
                                    request, data, endorserResponse, connection, callback);
                        }
                    });

        } catch (Exception e) {
            String errorMessage = "Fabric driver call exception: " + e;
            logger.error(errorMessage);
            TransactionResponse response = new TransactionResponse();
            TransactionException transactionException =
                    TransactionException.Builder.newInternalException(errorMessage);
            callback.onTransactionResponse(transactionException, response);
        }
    }

    @Override
    public void asyncSendTransactionByProxy(
            TransactionContext<TransactionRequest> request,
            Connection connection,
            Callback callback) {
        try {
            checkProxyRequest(request);

            TransactionContext<TransactionRequest> proxyRequest =
                    ProxyChaincodeResource.toProxyRequest(
                            request, ProxyChaincodeResource.MethodType.SENDTRANSACTION);

            byte[] data = EndorserRequestFactory.buildProposalRequestBytes(proxyRequest);
            TransactionParams transactionParams =
                    new TransactionParams(request.getData(), data, true);

            Request endorserRequest = new Request();
            endorserRequest.setData(transactionParams.toBytes());
            endorserRequest.setType(FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ENDORSER);
            endorserRequest.setResourceInfo(request.getResourceInfo());

            connection.asyncSend(
                    endorserRequest,
                    new Connection.Callback() {
                        @Override
                        public void onResponse(Response endorserResponse) {
                            asyncSendTransactionHandleEndorserResponse(
                                    request, data, endorserResponse, connection, callback);
                        }
                    });

        } catch (Exception e) {
            callback.onTransactionResponse(
                    new TransactionException(
                            TransactionException.ErrorCode.INTERNAL_ERROR,
                            "asyncSendTransactionByProxy exception: " + e),
                    null);
        }
    }

    @Override
    public void asyncGetBlockNumber(Connection connection, GetBlockNumberCallback callback) {
        // Test failed
        Request request = new Request();
        request.setType(FabricType.ConnectionMessage.FABRIC_GET_BLOCK_NUMBER);

        connection.asyncSend(
                request,
                new Connection.Callback() {
                    @Override
                    public void onResponse(Response response) {
                        if (response.getErrorCode()
                                == FabricType.TransactionResponseStatus.SUCCESS) {
                            long blockNumber = bytesToLong(response.getData());
                            logger.debug("Get block number: " + blockNumber);
                            callback.onResponse(null, blockNumber);
                        } else {
                            String errorMsg =
                                    "Get block number failed: " + response.getErrorMessage();
                            logger.error(errorMsg);
                            callback.onResponse(new Exception(errorMsg), -1);
                        }
                    }
                });
    }

    @Override
    public void asyncGetBlockHeader(
            long blockNumber, Connection connection, GetBlockHeaderCallback callback) {
        byte[] numberBytes = longToBytes(blockNumber);

        Request request = new Request();
        request.setType(FabricType.ConnectionMessage.FABRIC_GET_BLOCK_HEADER);
        request.setData(numberBytes);

        connection.asyncSend(
                request,
                new Connection.Callback() {
                    @Override
                    public void onResponse(Response response) {
                        if (response.getErrorCode()
                                == FabricType.TransactionResponseStatus.SUCCESS) {
                            callback.onResponse(null, response.getData());
                        } else {
                            String errorMsg =
                                    "Get block header failed: " + response.getErrorMessage();
                            logger.error(errorMsg);
                            callback.onResponse(new Exception(errorMsg), null);
                        }
                    }
                });
    }

    @Override
    public void asyncGetVerifiedTransaction(
            String transactionHash,
            long blockNumber,
            BlockHeaderManager blockHeaderManager,
            Connection connection,
            GetVerifiedTransactionCallback callback) {

        Request request = new Request();
        request.setType(FabricType.ConnectionMessage.FABRIC_GET_TRANSACTION);
        request.setData(transactionHash.getBytes(StandardCharsets.UTF_8));

        connection.asyncSend(
                request,
                new Connection.Callback() {
                    @Override
                    public void onResponse(Response response) {
                        try {
                            if (response.getErrorCode()
                                    == FabricType.TransactionResponseStatus.SUCCESS) {

                                // Generate Verified transaction
                                FabricTransaction fabricTransaction =
                                        FabricTransaction.buildFromEnvelopeBytes(
                                                response.getData());
                                String txID = fabricTransaction.getTxID();
                                String chaincodeName = fabricTransaction.getChaincodeName();

                                if (!transactionHash.equals(txID)) {
                                    throw new Exception(
                                            "Request txHash: "
                                                    + transactionHash
                                                    + " but response: "
                                                    + txID);
                                }

                                asyncVerifyTransactionOnChain(
                                        txID,
                                        blockNumber,
                                        blockHeaderManager,
                                        new Consumer<Boolean>() {
                                            @Override
                                            public void accept(Boolean hasOnChain) {

                                                if (!hasOnChain) {
                                                    callback.onResponse(
                                                            new Exception(
                                                                    "Verify failed. Tx("
                                                                            + txID
                                                                            + ") is invalid or not on block("
                                                                            + blockNumber
                                                                            + ")"),
                                                            null);
                                                } else {
                                                    TransactionRequest transactionRequest =
                                                            new TransactionRequest();
                                                    transactionRequest.setMethod(
                                                            fabricTransaction.getMethod());
                                                    transactionRequest.setArgs(
                                                            fabricTransaction
                                                                    .getArgs()
                                                                    .toArray(new String[] {}));

                                                    TransactionResponse transactionResponse =
                                                            decodeTransactionResponse(
                                                                    fabricTransaction
                                                                            .getOutputBytes());
                                                    transactionResponse.setHash(txID);
                                                    transactionResponse.setErrorCode(
                                                            FabricType.TransactionResponseStatus
                                                                    .SUCCESS);
                                                    transactionResponse.setBlockNumber(blockNumber);

                                                    VerifiedTransaction verifiedTransaction =
                                                            new VerifiedTransaction(
                                                                    blockNumber,
                                                                    txID,
                                                                    chaincodeName,
                                                                    transactionRequest,
                                                                    transactionResponse);
                                                    callback.onResponse(null, verifiedTransaction);
                                                }
                                            }
                                        });
                            } else {
                                callback.onResponse(
                                        new Exception(response.getErrorMessage()), null);
                            }
                        } catch (Exception e) {
                            callback.onResponse(e, null);
                        }
                    }
                });
    }

    private void asyncSendTransactionHandleEndorserResponse(
            TransactionContext<?> request,
            byte[] envelopeRequestData,
            Response endorserResponse,
            Connection connection,
            Driver.Callback callback) {
        if (endorserResponse.getErrorCode() != FabricType.TransactionResponseStatus.SUCCESS) {
            TransactionResponse response = new TransactionResponse();
            TransactionException transactionException =
                    new TransactionException(
                            endorserResponse.getErrorCode(), endorserResponse.getErrorMessage());
            callback.onTransactionResponse(transactionException, response);
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
                                        envelopeRequestData,
                                        ordererPayloadToSign,
                                        ordererResponse,
                                        callback);
                            }
                        });

            } catch (Exception e) {
                String errorMessage = "Fabric driver call orderer exception: " + e;
                logger.error(errorMessage);
                TransactionResponse response = new TransactionResponse();
                TransactionException transactionException =
                        TransactionException.Builder.newInternalException(errorMessage);
                callback.onTransactionResponse(transactionException, response);
            }
        }
    }

    private void asyncSendTransactionHandleOrdererResponse(
            TransactionContext<?> request,
            byte[] envelopeRequestData,
            byte[] ordererPayloadToSign,
            Response ordererResponse,
            Driver.Callback callback) {
        try {
            if (ordererResponse.getErrorCode() == FabricType.TransactionResponseStatus.SUCCESS) {
                // Success, verify transaction
                String txID = EndorserRequestFactory.getTxIDFromEnvelopeBytes(envelopeRequestData);
                long txBlockNumber = bytesToLong(ordererResponse.getData());

                asyncVerifyTransactionOnChain(
                        txID,
                        txBlockNumber,
                        request.getBlockHeaderManager(),
                        new Consumer<Boolean>() {
                            @Override
                            public void accept(Boolean verifyResult) {
                                TransactionResponse response = new TransactionResponse();
                                TransactionException transactionException = null;
                                try {
                                    if (!verifyResult) {
                                        transactionException =
                                                new TransactionException(
                                                        FabricType.TransactionResponseStatus
                                                                .FABRIC_TX_ONCHAIN_VERIFY_FAIED,
                                                        "Verify failed. Tx("
                                                                + txID
                                                                + ") is invalid or not on block("
                                                                + txBlockNumber
                                                                + ")");
                                    } else {
                                        response =
                                                decodeTransactionResponse(
                                                        FabricTransaction.buildFromPayloadBytes(
                                                                        ordererPayloadToSign)
                                                                .getOutputBytes());
                                        response.setHash(txID);
                                        response.setBlockNumber(txBlockNumber);
                                        response.setErrorCode(
                                                FabricType.TransactionResponseStatus.SUCCESS);
                                        response.setErrorMessage("Success");
                                        transactionException =
                                                TransactionException.Builder.newSuccessException();
                                    }
                                } catch (Exception e) {
                                    transactionException =
                                            new TransactionException(
                                                    FabricType.TransactionResponseStatus
                                                            .FABRIC_TX_ONCHAIN_VERIFY_FAIED,
                                                    "Verify failed. Tx("
                                                            + txID
                                                            + ") is invalid or not on block("
                                                            + txBlockNumber
                                                            + ") Internal error: "
                                                            + e);
                                }
                                callback.onTransactionResponse(transactionException, response);
                            }
                        });

            } else if (ordererResponse.getErrorCode()
                    == FabricType.TransactionResponseStatus.FABRIC_EXECUTE_CHAINCODE_FAILED) {
                TransactionResponse response = new TransactionResponse();
                Integer errorCode = new Integer(ordererResponse.getData()[0]);
                // If transaction execute failed, fabric TxValidationCode is in data
                TransactionException transactionException =
                        new TransactionException(
                                ordererResponse.getErrorCode(), ordererResponse.getErrorMessage());
                response.setErrorCode(errorCode);
                response.setErrorMessage(ordererResponse.getErrorMessage());
                callback.onTransactionResponse(transactionException, response);
            } else {
                TransactionResponse response = new TransactionResponse();
                TransactionException transactionException =
                        new TransactionException(
                                ordererResponse.getErrorCode(), ordererResponse.getErrorMessage());
                callback.onTransactionResponse(transactionException, response);
            }

        } catch (Exception e) {
            String errorMessage = "Fabric driver call handle orderer response exception: " + e;
            logger.error(errorMessage);
            TransactionResponse response = new TransactionResponse();
            response.setErrorCode(FabricType.TransactionResponseStatus.INTERNAL_ERROR);
            TransactionException transactionException =
                    TransactionException.Builder.newInternalException(errorMessage);
            callback.onTransactionResponse(transactionException, response);
        }
    }

    public void asyncInstallChaincode(
            TransactionContext<InstallChaincodeRequest> request,
            Connection connection,
            Driver.Callback callback) {
        try {
            checkInstallRequest(request);

            Request installRequest = EndorserRequestFactory.buildInstallProposalRequest(request);
            installRequest.setType(
                    FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ORG_ENDORSER);

            if (request.getResourceInfo() == null) {
                ResourceInfo resourceInfo = new ResourceInfo();
                installRequest.setResourceInfo(resourceInfo);
            }
            installRequest
                    .getResourceInfo()
                    .getProperties()
                    .put(
                            FabricType.ORG_NAME_DEF,
                            new String[] {
                                request.getData().getOrgName()
                            }); // install has only 1 org

            connection.asyncSend(
                    installRequest,
                    new Connection.Callback() {
                        @Override
                        public void onResponse(Response connectionResponse) {
                            TransactionResponse response = new TransactionResponse();
                            TransactionException transactionException;
                            try {
                                if (connectionResponse.getErrorCode()
                                        == FabricType.TransactionResponseStatus.SUCCESS) {
                                    response =
                                            decodeTransactionResponse(connectionResponse.getData());
                                    response.setHash(
                                            EndorserRequestFactory.getTxIDFromEnvelopeBytes(
                                                    installRequest.getData()));
                                }
                                transactionException =
                                        new TransactionException(
                                                connectionResponse.getErrorCode(),
                                                connectionResponse.getErrorMessage());
                            } catch (Exception e) {
                                String errorMessage =
                                        "Fabric driver install chaincode onResponse exception: "
                                                + e;
                                logger.error(errorMessage);
                                transactionException =
                                        TransactionException.Builder.newInternalException(
                                                errorMessage);
                            }
                            callback.onTransactionResponse(transactionException, response);
                        }
                    });

        } catch (Exception e) {
            String errorMessage = "Fabric driver deploy install exception: " + e;
            logger.error(errorMessage);
            TransactionResponse response = new TransactionResponse();
            TransactionException transactionException =
                    TransactionException.Builder.newInternalException(errorMessage);
            callback.onTransactionResponse(transactionException, response);
        }
    }

    public void asyncInstantiateChaincode(
            TransactionContext<InstantiateChaincodeRequest> request,
            Connection connection,
            Driver.Callback callback) {
        try {
            checkInstantiateRequest(request);

            Request instantiateRequest =
                    EndorserRequestFactory.buildInstantiateProposalRequest(request);
            instantiateRequest.setType(
                    FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ORG_ENDORSER);

            if (request.getResourceInfo() == null) {
                ResourceInfo resourceInfo = new ResourceInfo();
                instantiateRequest.setResourceInfo(resourceInfo);
            }
            instantiateRequest
                    .getResourceInfo()
                    .getProperties()
                    .put(FabricType.ORG_NAME_DEF, request.getData().getOrgNames()); // set org name

            connection.asyncSend(
                    instantiateRequest,
                    new Connection.Callback() {
                        @Override
                        public void onResponse(Response endorserResponse) {
                            asyncSendTransactionHandleEndorserResponse(
                                    request,
                                    instantiateRequest.getData(),
                                    endorserResponse,
                                    connection,
                                    callback);
                        }
                    });

        } catch (Exception e) {
            String errorMessage = "Fabric driver deploy instantiate exception: " + e;
            logger.error(errorMessage);
            TransactionResponse response = new TransactionResponse();
            TransactionException transactionException =
                    TransactionException.Builder.newInternalException(errorMessage);
            callback.onTransactionResponse(transactionException, response);
        }
    }

    @Override
    public void asyncCustomCommand(
            String command,
            Path path,
            Object[] args,
            Account account,
            BlockHeaderManager blockHeaderManager,
            Connection connection,
            CustomCommandCallback callback) {
        switch (command) {
            case InstallCommand.NAME:
                handleInstallCommand(args, account, blockHeaderManager, connection, callback);
                break;
            case InstantiateCommand.NAME:
                handleInstantiateCommand(args, account, blockHeaderManager, connection, callback);
                break;
            default:
                callback.onResponse(new Exception("Unsupported command for Fabric plugin"), null);
                break;
        }
    }

    private void handleInstallCommand(
            Object[] args,
            Account account,
            BlockHeaderManager blockHeaderManager,
            Connection connection,
            CustomCommandCallback callback) {

        try {
            FabricConnection.Properties properties =
                    FabricConnection.Properties.parseFromMap(connection.getProperties());
            String channelName = properties.getChannelName();
            if (channelName == null) {
                throw new Exception("Connection properties(ChannelName) is not set");
            }

            InstallChaincodeRequest installChaincodeRequest =
                    InstallCommand.parseEncodedArgs(args, channelName); // parse args from sdk

            TransactionContext<InstallChaincodeRequest> installRequest =
                    new TransactionContext<InstallChaincodeRequest>(
                            installChaincodeRequest, account, null, null, blockHeaderManager);

            asyncInstallChaincode(
                    installRequest,
                    connection,
                    new Driver.Callback() {
                        @Override
                        public void onTransactionResponse(
                                TransactionException transactionException,
                                TransactionResponse transactionResponse) {
                            if (transactionException.isSuccess()) {
                                callback.onResponse(null, new String("Success"));
                            } else {
                                callback.onResponse(
                                        transactionException,
                                        new String("Failed: ") + transactionException.getMessage());
                            }
                        }
                    });

        } catch (Exception e) {
            callback.onResponse(e, new String("Failed: ") + e.getMessage());
        }
    }

    private void handleInstantiateCommand(
            Object[] args,
            Account account,
            BlockHeaderManager blockHeaderManager,
            Connection connection,
            CustomCommandCallback callback) {
        try {
            FabricConnection.Properties properties =
                    FabricConnection.Properties.parseFromMap(connection.getProperties());
            String channelName = properties.getChannelName();
            if (channelName == null) {
                throw new Exception("Connection properties(ChannelName) is not set");
            }

            InstantiateChaincodeRequest instantiateChaincodeRequest =
                    InstantiateCommand.parseEncodedArgs(args, channelName);

            TransactionContext<InstantiateChaincodeRequest> instantiateRequest =
                    new TransactionContext<InstantiateChaincodeRequest>(
                            instantiateChaincodeRequest, account, null, null, blockHeaderManager);

            asyncInstantiateChaincode(
                    instantiateRequest,
                    connection,
                    new Driver.Callback() {
                        @Override
                        public void onTransactionResponse(
                                TransactionException transactionException,
                                TransactionResponse transactionResponse) {
                            if (transactionException.isSuccess()) {
                                callback.onResponse(null, new String("Success"));
                            } else {
                                callback.onResponse(
                                        transactionException,
                                        new String("Failed: ") + transactionException.getMessage());
                            }
                        }
                    });
            callback.onResponse(
                    null,
                    new String("Query success. Please wait and use 'listResources' to check."));

        } catch (Exception e) {
            callback.onResponse(e, new String("Failed: ") + e.getMessage());
        }
    }

    private void asyncVerifyTransactionOnChain(
            String txID,
            long blockNumber,
            BlockHeaderManager blockHeaderManager,
            Consumer<Boolean> callback) {
        logger.debug("To verify transaction, waiting fabric block syncing ...");
        blockHeaderManager.asyncGetBlockHeader(
                blockNumber,
                new BlockHeaderManager.GetBlockHeaderCallback() {
                    @Override
                    public void onResponse(Exception e, byte[] blockHeader) {
                        logger.debug("Receive block, verify transaction ...");
                        try {
                            FabricBlock block = FabricBlock.encode(blockHeader);
                            boolean verifyResult = block.hasTransaction(txID);
                            logger.debug(
                                    "Tx(block: "
                                            + blockNumber
                                            + "): "
                                            + txID
                                            + " verify: "
                                            + verifyResult);
                            callback.accept(verifyResult);
                        } catch (Exception e1) {
                            callback.accept(false);
                        }
                    }
                });
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

    private void checkProxyRequest(TransactionContext<TransactionRequest> request)
            throws Exception {
        if (request.getResourceInfo() == null) {
            throw new Exception("resourceInfo is null");
        }

        String isTemporary = (String) request.getResourceInfo().getProperties().get("isTemporary");
        if (isTemporary != null && isTemporary.equals("true")) {
            throw new Exception(
                    "Fabric resource " + request.getResourceInfo().getName() + " not found");
        }
    }

    private void checkInstallRequest(TransactionContext<InstallChaincodeRequest> request)
            throws Exception {
        if (request.getData() == null) {
            throw new Exception("Request data is null");
        }
        request.getData().check();

        if (request.getAccount() == null) {
            throw new Exception("Unkown account: " + request.getAccount());
        }

        if (!request.getAccount().getType().equals(FabricType.Account.FABRIC_ACCOUNT)) {
            throw new Exception(
                    "Illegal account type for fabric call: " + request.getAccount().getType());
        }
    }

    private void checkInstantiateRequest(TransactionContext<InstantiateChaincodeRequest> request)
            throws Exception {
        if (request.getData() == null) {
            throw new Exception("Request data is null");
        }
        request.getData().check();

        if (request.getAccount() == null) {
            throw new Exception("Unkown account: " + request.getAccount());
        }

        if (!request.getAccount().getType().equals(FabricType.Account.FABRIC_ACCOUNT)) {
            throw new Exception(
                    "Illegal account type for fabric call: " + request.getAccount().getType());
        }
    }
}
