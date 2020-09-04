package com.webank.wecross.stub.fabric.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.TransactionContext;
import com.webank.wecross.stub.TransactionRequest;
import com.webank.wecross.stub.fabric.ChaincodeResource;
import com.webank.wecross.stub.fabric.ResourceInfoProperty;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyChaincodeResource extends ChaincodeResource {
    private static Logger logger = LoggerFactory.getLogger(ProxyChaincodeResource.class);
    public static final String DEFAULT_NAME = "WeCrossProxy";

    public enum MethodType {
        CALL,
        SENDTRANSACTION
    }

    private static ObjectMapper objectMapper = new ObjectMapper();

    ProxyChaincodeResource(String channelName, String proxyVersion) {
        super(DEFAULT_NAME, DEFAULT_NAME, proxyVersion, channelName);
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
                        context.getBlockHeaderManager());
        logger.debug("toProxyConstantCallRequest: " + transactionContext.toString());
        return new ImmutablePair<>(transactionContext, proxyRequest);
    }

    static class ChaincodeArgs {
        public String[] args;

        public ChaincodeArgs() {}

        public ChaincodeArgs(String[] args) {
            this.args = args;
        }
    }

    private static String[] buildConstantCallArgs(
            TransactionContext transactionContext, TransactionRequest transactionRequest)
            throws Exception {
        // transactionID, path, method, argsJsonString

        String transactionID =
                "0"; // 0 means the request is not belongs to any transaction(routine)
        if (transactionRequest.getOptions() != null
                && transactionRequest.getOptions().get("transactionID") != null) {
            transactionID = (String) transactionRequest.getOptions().get("transactionID");
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

        String argsJsonString = objectMapper.writeValueAsString(new ChaincodeArgs(chaincodeArgs));

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
                        context.getBlockHeaderManager());

        return new ImmutablePair<TransactionContext, TransactionRequest>(
                transactionContext, proxyRequest);
    }

    private static String[] buildSendTransactionArgs(
            TransactionContext transactionContext, TransactionRequest transactionRequest)
            throws Exception {
        // transactionID, seq, path, method, argsJsonString

        String transactionID =
                "0"; // 0 means the request is not belongs to any transaction(routine)
        if (transactionRequest.getOptions() != null
                && transactionRequest.getOptions().get("TRANSACTION_ID") != null) {
            transactionID = (String) transactionRequest.getOptions().get("TRANSACTION_ID");
        }

        String seq = "0";
        if (transactionRequest.getOptions() != null
                && transactionRequest.getOptions().get("TRANSACTION_SEQ") != null) {
            seq = (String) transactionRequest.getOptions().get("TRANSACTION_SEQ");
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

        String argsJsonString = objectMapper.writeValueAsString(new ChaincodeArgs(chaincodeArgs));

        String[] args = {transactionID, seq, path, method, argsJsonString};

        return args;
    }

    public static String[] decodeSendTransactionArgs(String[] sendTransactionArgs)
            throws Exception {

        if (sendTransactionArgs.length != 5) {
            throw new Exception(
                    "WeCrossProxy sendTransactionArgs length is not 5 but: "
                            + sendTransactionArgs.length);
        }

        try {
            String argsJsonString = sendTransactionArgs[4];

            ChaincodeArgs chaincodeArgs =
                    objectMapper.readValue(argsJsonString, ChaincodeArgs.class);

            return chaincodeArgs.args;
        } catch (Exception e) {
            throw new Exception("decodeSendTransactionArgs exception: " + e);
        }
    }

    public static String decodeSendTransactionArgsMethod(String[] sendTransactionArgs)
            throws Exception {

        if (sendTransactionArgs.length != 5) {
            throw new Exception(
                    "WeCrossProxy sendTransactionArgs length is not 5 but: "
                            + sendTransactionArgs.length);
        }

        try {
            String method = sendTransactionArgs[3];

            return method;
        } catch (Exception e) {
            throw new Exception("decodeSendTransactionArgsMethod exception: " + e);
        }
    }
}
