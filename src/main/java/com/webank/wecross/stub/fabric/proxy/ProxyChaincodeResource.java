package com.webank.wecross.stub.fabric.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.TransactionContext;
import com.webank.wecross.stub.TransactionRequest;
import com.webank.wecross.stub.fabric.ChaincodeResource;

public class ProxyChaincodeResource extends ChaincodeResource {
    public static final String NAME = "WeCrossProxy";

    public enum MethodType {
        CALL,
        SENDTRANSACTION
    }

    private static ObjectMapper objectMapper = new ObjectMapper();

    ProxyChaincodeResource() {
        super(NAME, NAME);
    }

    public static ProxyChaincodeResource build() {
        return new ProxyChaincodeResource();
    }

    public static ResourceInfo defaultResourceInfo() {
        return build().getResourceInfo();
    }

    public static TransactionContext<TransactionRequest> toProxyRequest(
            TransactionContext<TransactionRequest> context, MethodType methodType)
            throws Exception {
        switch (methodType) {
            case CALL:
                return toProxyRequestByMethod(context, "constantCall");
            case SENDTRANSACTION:
                return toProxyRequestByMethod(context, "sendTransaction");
            default:
                throw new Exception("Unsupported MethodType: " + methodType);
        }
    }

    private static TransactionContext<TransactionRequest> toProxyRequestByMethod(
            TransactionContext<TransactionRequest> context, String method) throws Exception {
        TransactionRequest proxyRequest = new TransactionRequest();
        proxyRequest.setMethod(method);
        proxyRequest.setArgs(buildArgs(context));

        TransactionContext<TransactionRequest> transactionContext =
                new TransactionContext<>(
                        proxyRequest,
                        context.getAccount(),
                        defaultResourceInfo(),
                        context.getBlockHeaderManager());

        return transactionContext;
    }

    private static String[] buildArgs(TransactionContext<TransactionRequest> context)
            throws Exception {
        // transactionID, path, method, argsJsonString

        String transactionID = (String) context.getData().getOptions().get("transactionID");
        if (transactionID == null) {
            throw new Exception("transactionID not set in: " + context.toString());
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

        String argsJsonString = objectMapper.writeValueAsString(chaincodeArgs);

        String[] args = {transactionID, path, method, argsJsonString};

        return args;
    }
}
