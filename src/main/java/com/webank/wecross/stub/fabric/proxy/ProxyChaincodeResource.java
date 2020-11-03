package com.webank.wecross.stub.fabric.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.StubConstant;
import com.webank.wecross.stub.TransactionContext;
import com.webank.wecross.stub.TransactionRequest;
import com.webank.wecross.stub.fabric.ChaincodeResource;
import com.webank.wecross.stub.fabric.ResourceInfoProperty;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyChaincodeResource extends ChaincodeResource {
    private static Logger logger = LoggerFactory.getLogger(ProxyChaincodeResource.class);

    public enum MethodType {
        CALL,
        SENDTRANSACTION
    }

    private static ObjectMapper objectMapper = new ObjectMapper();

    ProxyChaincodeResource(String channelName, String proxyVersion) {
        super(StubConstant.PROXY_NAME, StubConstant.PROXY_NAME, proxyVersion, channelName);
    }

    public static ProxyChaincodeResource build(String channelName, String proxyVersion) {
        return new ProxyChaincodeResource(channelName, proxyVersion);
    }

    public static ResourceInfo toProxyResourceInfo(ResourceInfo originInfo) throws Exception {
        ResourceInfoProperty property = ResourceInfoProperty.parseFrom(originInfo.getProperties());
        if (property.getChannelName() == null || property.getChannelName().equals("")) {
            throw new Exception("ChannelName is null");
        }

        return build(property.getChannelName(), property.getVersion()).getResourceInfo();
    }

    public static ImmutablePair<TransactionContext, TransactionRequest> toProxyRequest(
            TransactionContext transactionContext,
            TransactionRequest transactionRequest,
            MethodType methodType)
            throws Exception {
        switch (methodType) {
            case CALL:
                return toProxyConstantCallRequest(transactionContext, transactionRequest);
            case SENDTRANSACTION:
                return toProxySendTransactionRequest(transactionContext, transactionRequest);
            default:
                throw new Exception("Unsupported MethodType: " + methodType);
        }
    }

    private static ImmutablePair<TransactionContext, TransactionRequest> toProxyConstantCallRequest(
            TransactionContext context, TransactionRequest transactionRequest) throws Exception {
        TransactionRequest proxyRequest = new TransactionRequest();
        proxyRequest.setMethod("constantCall");
        proxyRequest.setArgs(buildConstantCallArgs(context, transactionRequest));

        TransactionContext transactionContext =
                new TransactionContext(
                        context.getAccount(),
                        context.getPath(),
                        toProxyResourceInfo(context.getResourceInfo()),
                        context.getBlockManager());

        logger.debug(
                "toProxyConstantCallRequest,  transactionContext: {}, proxyRequest: {} ",
                transactionContext,
                proxyRequest);
        return new ImmutablePair<>(transactionContext, proxyRequest);
    }

    private static String[] buildConstantCallArgs(
            TransactionContext transactionContext, TransactionRequest transactionRequest)
            throws Exception {
        // transactionID, path, method, argsJsonString

        String transactionID =
                "0"; // 0 means the request is not belongs to any transaction(routine)
        if (transactionRequest.getOptions() != null
                && transactionRequest.getOptions().get(StubConstant.XA_TRANSACTION_ID) != null) {
            transactionID =
                    (String) transactionRequest.getOptions().get(StubConstant.XA_TRANSACTION_ID);
        }

        String path = transactionContext.getPath().toString();
        if (path == null) {
            throw new Exception("path not set in: " + transactionContext.toString());
        }

        String method = transactionRequest.getMethod();
        if (method == null) {
            throw new Exception("method not set in: " + transactionContext.toString());
        }

        String[] chaincodeArgs = transactionRequest.getArgs();
        if (chaincodeArgs == null) {
            chaincodeArgs = new String[] {}; // Just pass empty string[]
        }

        String argsJsonString = objectMapper.writeValueAsString(chaincodeArgs);

        String[] args = {transactionID, path, method, argsJsonString};

        return args;
    }

    private static ImmutablePair<TransactionContext, TransactionRequest>
            toProxySendTransactionRequest(
                    TransactionContext context, TransactionRequest transactionRequest)
                    throws Exception {
        TransactionRequest proxyRequest = new TransactionRequest();
        proxyRequest.setMethod("sendTransaction");
        proxyRequest.setArgs(buildSendTransactionArgs(context, transactionRequest));

        TransactionContext transactionContext =
                new TransactionContext(
                        context.getAccount(),
                        context.getPath(),
                        toProxyResourceInfo(context.getResourceInfo()),
                        context.getBlockManager());

        logger.debug(
                "toProxySendTransactionRequest,  transactionContext: {}, proxyRequest: {} ",
                transactionContext,
                proxyRequest);
        return new ImmutablePair<>(transactionContext, proxyRequest);
    }

    private static String[] buildSendTransactionArgs(
            TransactionContext transactionContext, TransactionRequest transactionRequest)
            throws Exception {
        // transactionID, seq, path, method, argsJsonString

        String uniqueID =
                (String) transactionRequest.getOptions().get(StubConstant.TRANSACTION_UNIQUE_ID);
        String uid =
                Objects.nonNull(uniqueID)
                        ? uniqueID
                        : UUID.randomUUID().toString().replaceAll("-", "");

        String transactionID =
                "0"; // 0 means the request is not belongs to any transaction(routine)
        if (transactionRequest.getOptions() != null
                && transactionRequest.getOptions().get(StubConstant.XA_TRANSACTION_ID) != null) {
            transactionID =
                    (String) transactionRequest.getOptions().get(StubConstant.XA_TRANSACTION_ID);
        }

        long seq = 0;
        if (transactionRequest.getOptions() != null
                && transactionRequest.getOptions().get(StubConstant.XA_TRANSACTION_SEQ) != null) {
            seq = (long) transactionRequest.getOptions().get(StubConstant.XA_TRANSACTION_SEQ);
        }

        String path = transactionContext.getPath().toString();
        if (path == null) {
            throw new Exception("path not set in: " + transactionContext.toString());
        }

        String method = transactionRequest.getMethod();
        if (method == null) {
            throw new Exception("method not set in: " + transactionContext.toString());
        }

        String[] chaincodeArgs = transactionRequest.getArgs();
        if (chaincodeArgs == null) {
            chaincodeArgs = new String[] {}; // Just pass empty string[]
        }

        String argsJsonString = objectMapper.writeValueAsString(chaincodeArgs);

        String[] args = {uid, transactionID, String.valueOf(seq), path, method, argsJsonString};

        return args;
    }

    public static String[] decodeSendTransactionArgs(String[] sendTransactionArgs)
            throws Exception {

        if (sendTransactionArgs.length != 6) {
            throw new Exception(
                    "WeCrossProxy sendTransactionArgs length is not 5 but: "
                            + sendTransactionArgs.length);
        }

        try {
            String argsJsonString = sendTransactionArgs[5];

            return objectMapper.readValue(argsJsonString, String[].class);
        } catch (Exception e) {
            throw new Exception("decodeSendTransactionArgs exception: " + e);
        }
    }

    public static String decodeSendTransactionArgsMethod(String[] sendTransactionArgs)
            throws Exception {

        if (sendTransactionArgs.length != 6) {
            throw new Exception(
                    "WeCrossProxy sendTransactionArgs length is not 5 but: "
                            + sendTransactionArgs.length);
        }

        try {
            String method = sendTransactionArgs[4];

            return method;
        } catch (Exception e) {
            throw new Exception("decodeSendTransactionArgsMethod exception: " + e);
        }
    }
}
