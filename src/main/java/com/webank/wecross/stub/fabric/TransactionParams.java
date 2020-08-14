package com.webank.wecross.stub.fabric;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.wecross.stub.TransactionRequest;
import java.util.Arrays;

public class TransactionParams {
    private static ObjectMapper objectMapper = new ObjectMapper();

    private TransactionRequest originTransactionRequest;
    private byte[] data;
    private String[] orgNames;
    private boolean byProxy;

    public TransactionParams() {}

    public TransactionParams(
            TransactionRequest originTransactionRequest, byte[] data, boolean byProxy) {
        this.originTransactionRequest = originTransactionRequest;
        this.data = data;
        this.byProxy = byProxy;
    }

    @JsonIgnore
    public byte[] toBytes() throws Exception {
        return objectMapper.writeValueAsBytes(this);
    }

    public static TransactionParams parseFrom(byte[] bytes) throws Exception {
        return objectMapper.readValue(bytes, TransactionParams.class);
    }

    public TransactionRequest getOriginTransactionRequest() {
        return originTransactionRequest;
    }

    public void setOriginTransactionRequest(TransactionRequest originTransactionRequest) {
        this.originTransactionRequest = originTransactionRequest;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public boolean isByProxy() {
        return byProxy;
    }

    public void setByProxy(boolean byProxy) {
        this.byProxy = byProxy;
    }

    public String[] getOrgNames() {
        return orgNames;
    }

    public void setOrgNames(String[] orgNames) {
        this.orgNames = orgNames;
    }

    @Override
    public String toString() {
        return "TransactionParams{"
                + "originTransactionRequest="
                + originTransactionRequest
                + ", data="
                + Arrays.toString(data)
                + ", orgNames="
                + Arrays.toString(orgNames)
                + ", byProxy="
                + byProxy
                + '}';
    }
}
