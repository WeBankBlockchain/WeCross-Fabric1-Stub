package com.webank.wecross.stub.fabric;

import com.webank.wecross.common.FabricType;
import java.util.HashMap;
import java.util.Map;
import org.hyperledger.fabric.sdk.TransactionRequest;

public class ResourceInfoProperty {
    private String channelName;
    private String chainCodeName;
    private org.hyperledger.fabric.sdk.TransactionRequest.Type chainCodeType;
    private long proposalWaitTime;

    public static ResourceInfoProperty build() {
        return new ResourceInfoProperty();
    }

    public ResourceInfoProperty channelName(String channelName) {
        this.channelName = channelName;
        return this;
    }

    public ResourceInfoProperty chainCodeName(String chainCodeName) {
        this.chainCodeName = chainCodeName;
        return this;
    }

    public ResourceInfoProperty chainCodeType(
            org.hyperledger.fabric.sdk.TransactionRequest.Type chainCodeType) {
        this.chainCodeType = chainCodeType;
        return this;
    }

    public ResourceInfoProperty chainCodeType(String chainCodeType) {
        this.chainCodeType = stringTochainCodeType(chainCodeType);
        return this;
    }

    public ResourceInfoProperty proposalWaitTime(long proposalWaitTime) {
        this.proposalWaitTime = proposalWaitTime;
        return this;
    }

    public Map<Object, Object> toMap() {
        Map<Object, Object> properties = new HashMap<>();
        properties.put(FabricType.ResourceInfoProperty.CHANNEL_NAME, channelName);
        properties.put(FabricType.ResourceInfoProperty.CHAINCODE_NAME, chainCodeName);
        properties.put(
                FabricType.ResourceInfoProperty.CHAINCODE_TYPE,
                chainCodeTypeToString(chainCodeType));
        properties.put(
                FabricType.ResourceInfoProperty.PROPOSAL_WAIT_TIME,
                Long.toString(proposalWaitTime, 10));
        return properties;
    }

    public static ResourceInfoProperty parseFrom(Map<Object, Object> properties) throws Exception {
        return build().channelName(
                        (String) properties.get(FabricType.ResourceInfoProperty.CHANNEL_NAME))
                .chainCodeName(
                        (String) properties.get(FabricType.ResourceInfoProperty.CHAINCODE_NAME))
                .chainCodeType(
                        (String) properties.get(FabricType.ResourceInfoProperty.CHAINCODE_TYPE))
                .proposalWaitTime(
                        Long.parseLong(
                                (String)
                                        properties.get(
                                                FabricType.ResourceInfoProperty.PROPOSAL_WAIT_TIME),
                                10));
    }

    public String getChannelName() {
        return this.channelName;
    }

    public String getChainCodeName() {
        return chainCodeName;
    }

    public TransactionRequest.Type getChainCodeType() {
        return chainCodeType;
    }

    public long getProposalWaitTime() {
        return proposalWaitTime;
    }

    private static org.hyperledger.fabric.sdk.TransactionRequest.Type stringTochainCodeType(
            String type) {
        switch (type) {
            case "JAVA":
                return org.hyperledger.fabric.sdk.TransactionRequest.Type.JAVA;
            case "GO_LANG":
                return org.hyperledger.fabric.sdk.TransactionRequest.Type.GO_LANG;
            case "NONE":
            default:
                return org.hyperledger.fabric.sdk.TransactionRequest.Type.NODE;
        }
    }

    private static String chainCodeTypeToString(
            org.hyperledger.fabric.sdk.TransactionRequest.Type type) {
        switch (type) {
            case JAVA:
                return "JAVA";
            case GO_LANG:
                return "GO_LANG";
            case NODE:
            default:
                return "NONE";
        }
    }
}
