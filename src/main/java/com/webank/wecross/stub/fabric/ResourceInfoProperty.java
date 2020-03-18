package com.webank.wecross.stub.fabric;

import com.webank.wecross.common.FabricType;
import java.util.HashMap;
import java.util.Map;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.TransactionRequest;

public class ResourceInfoProperty {
    private Channel channel;
    private String chainCodeName;
    private org.hyperledger.fabric.sdk.TransactionRequest.Type chainCodeType;
    private long proposalWaitTime;

    public static ResourceInfoProperty build() {
        return new ResourceInfoProperty();
    }

    public ResourceInfoProperty channel(Channel channel) {
        this.channel = channel;
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

    public ResourceInfoProperty proposalWaitTime(long proposalWaitTime) {
        this.proposalWaitTime = proposalWaitTime;
        return this;
    }

    public Map<Object, Object> toMap() {
        Map<Object, Object> properties = new HashMap<>();
        properties.put(FabricType.ResourceInfoProperty.CHANNEL_NAME, channel);
        properties.put(FabricType.ResourceInfoProperty.CHAINCODE_NAME, chainCodeName);
        properties.put(FabricType.ResourceInfoProperty.CHAINCODE_TYPE, chainCodeType);
        properties.put(FabricType.ResourceInfoProperty.PROPOSAL_WAIT_TIME, proposalWaitTime);
        return properties;
    }

    public static ResourceInfoProperty parseFrom(Map<Object, Object> properties) throws Exception {
        return build().channel(
                        (Channel) properties.get(FabricType.ResourceInfoProperty.CHANNEL_NAME))
                .chainCodeName(
                        (String) properties.get(FabricType.ResourceInfoProperty.CHAINCODE_NAME))
                .chainCodeType(
                        (TransactionRequest.Type)
                                properties.get(FabricType.ResourceInfoProperty.CHAINCODE_TYPE))
                .proposalWaitTime(
                        (long) properties.get(FabricType.ResourceInfoProperty.PROPOSAL_WAIT_TIME));
    }

    public Channel getChannel() {
        return channel;
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
}
