package com.webank.wecross.stub.fabric.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.TransactionContext;
import com.webank.wecross.stub.TransactionRequest;
import com.webank.wecross.stub.fabric.ChaincodeResource;
import com.webank.wecross.stub.fabric.ResourceInfoProperty;

public class ProxyChaincodeResource extends ChaincodeResource {
    public static final String NAME = "WeCrossProxy";

    public enum MethodType {
        CALL,
        SENDTRANSACTION
    }

    private static ObjectMapper objectMapper = new ObjectMapper();

    ProxyChaincodeResource(String channelName) {
        super(NAME, NAME, channelName);
    }

    public static ProxyChaincodeResource build(String channelName) {
        return new ProxyChaincodeResource(channelName);
    }

    public static ResourceInfo toProxyResourceInfo(ResourceInfo originInfo) throws Exception {
        ResourceInfoProperty property = ResourceInfoProperty.parseFrom(originInfo.getProperties());
        if (property.getChannelName() == null || property.getChannelName().equals("")) {
            throw new Exception("ChannelName is null");
        }

        return build(property.getChannelName()).getResourceInfo();
    }

    public static TransactionContext<TransactionRequest> toProxyRequest(
            TransactionContext<TransactionRequest> context, MethodType methodType)
            throws Exception {
        switch (methodType) {
            case CALL:
                return toProxyConstantCallRequest(context);
            case SENDTRANSACTION:
                return toProxySendTransactionRequest(context);
            default:
                throw new Exception("Unsupported MethodType: " + methodType);
        }
    }

    private static TransactionContext<TransactionRequest> toProxyConstantCallRequest(
            TransactionContext<TransactionRequest> context) throws Exception {
        TransactionRequest proxyRequest = new TransactionRequest();
        proxyRequest.setMethod("constantCall");
        proxyRequest.setArgs(buildConstantCallArgs(context));

        TransactionContext<TransactionRequest> transactionContext =
                new TransactionContext<>(
                        proxyRequest,
                        context.getAccount(),
                        toProxyResourceInfo(context.getResourceInfo()),
                        context.getBlockHeaderManager());

        return transactionContext;
    }

    private static String[] buildConstantCallArgs(TransactionContext<TransactionRequest> context)
            throws Exception {
        // transactionID, path, method, argsJsonString

        String transactionID =
                "0"; // 0 means the request is not belongs to any transaction(routine)
        if (context.getData().getOptions() != null
                && context.getData().getOptions().get("transactionID") != null) {
            transactionID = (String) context.getData().getOptions().get("transactionID");
        }

        String path = context.getData().getPath();
        if (path == null) {
            throw new Exception("path not set in: " + context.toString());
        }

        String method = context.getData().getMethod();
        if (method == null) {
            throw new Exception("method not set in: " + context.toString());
        }

        String[] chaincodeArgs = context.getData().getArgs();
        if (chaincodeArgs == null) {
            chaincodeArgs = new String[] {}; // Just pass empty string[]
        }

        class ChaincodeArgs {
            public String[] args;

            public ChaincodeArgs(String[] args) {
                this.args = args;
            }
        }

        String argsJsonString = objectMapper.writeValueAsString(new ChaincodeArgs(chaincodeArgs));

        String[] args = {transactionID, path, method, argsJsonString};

        return args;
    }

    private static TransactionContext<TransactionRequest> toProxySendTransactionRequest(
            TransactionContext<TransactionRequest> context) throws Exception {
        TransactionRequest proxyRequest = new TransactionRequest();
        proxyRequest.setMethod("sendTransaction");
        proxyRequest.setArgs(buildSendTransactionArgs(context));

        TransactionContext<TransactionRequest> transactionContext =
                new TransactionContext<>(
                        proxyRequest,
                        context.getAccount(),
                        toProxyResourceInfo(context.getResourceInfo()),
                        context.getBlockHeaderManager());

        return transactionContext;
    }

    private static String[] buildSendTransactionArgs(TransactionContext<TransactionRequest> context)
            throws Exception {
        // transactionID, seq, path, method, argsJsonString

        String transactionID =
                "0"; // 0 means the request is not belongs to any transaction(routine)
        if (context.getData().getOptions() != null
                && context.getData().getOptions().get("transactionID") != null) {
            transactionID = (String) context.getData().getOptions().get("transactionID");
        }

        String seq = "0";
        if (context.getData().getOptions() != null
                && context.getData().getOptions().get("seq") != null) {
            seq = (String) context.getData().getOptions().get("seq");
        }

        String path = context.getData().getPath();
        if (path == null) {
            throw new Exception("path not set in: " + context.toString());
        }

        String method = context.getData().getMethod();
        if (method == null) {
            throw new Exception("method not set in: " + context.toString());
        }

        String[] chaincodeArgs = context.getData().getArgs();
        if (chaincodeArgs == null) {
            chaincodeArgs = new String[] {}; // Just pass empty string[]
        }

        class ChaincodeArgs {
            public String[] args;

            public ChaincodeArgs(String[] args) {
                this.args = args;
            }
        }

        String argsJsonString = objectMapper.writeValueAsString(new ChaincodeArgs(chaincodeArgs));

        String[] args = {transactionID, seq, path, method, argsJsonString};

        return args;
    }
}
