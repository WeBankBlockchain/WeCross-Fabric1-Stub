package com.webank.wecross.stub.fabric;

import com.webank.wecross.common.FabricType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ResourceInfoProperty {
    private String channelName;
    private String chainCodeName;
    private String version;
    private ArrayList<String> orgNames;
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

    public ResourceInfoProperty version(String version) {
        this.version = version;
        return this;
    }

    public ResourceInfoProperty proposalWaitTime(long proposalWaitTime) {
        this.proposalWaitTime = proposalWaitTime;
        return this;
    }

    public ResourceInfoProperty orgNames(ArrayList<String> orgNames) {
        this.orgNames = orgNames;
        return this;
    }

    public ResourceInfoProperty orgNames(String[] orgNames) {
        ArrayList<String> inputs = new ArrayList<>();
        for (String name : orgNames) {
            inputs.add(name);
        }

        return orgNames(inputs);
    }

    public Map<Object, Object> toMap() {
        Map<Object, Object> properties = new HashMap<>();
        properties.put(FabricType.ResourceInfoProperty.CHANNEL_NAME, channelName);
        properties.put(FabricType.ResourceInfoProperty.CHAINCODE_NAME, chainCodeName);
        properties.put(FabricType.ResourceInfoProperty.CHAINCODE_VERSION, version);
        properties.put(
                FabricType.ResourceInfoProperty.PROPOSAL_WAIT_TIME,
                Long.toString(proposalWaitTime, 10));
        properties.put(FabricType.ResourceInfoProperty.ORG_NAMES, orgNames);
        return properties;
    }

    public static ResourceInfoProperty parseFrom(Map<Object, Object> properties) throws Exception {
        checkParams(properties);

        return build().channelName(
                        (String) properties.get(FabricType.ResourceInfoProperty.CHANNEL_NAME))
                .chainCodeName(
                        (String) properties.get(FabricType.ResourceInfoProperty.CHAINCODE_NAME))
                .version((String) properties.get(FabricType.ResourceInfoProperty.CHAINCODE_VERSION))
                .proposalWaitTime(
                        Long.parseLong(
                                (String)
                                        properties.get(
                                                FabricType.ResourceInfoProperty.PROPOSAL_WAIT_TIME),
                                10))
                .orgNames(
                        (ArrayList<String>)
                                properties.get(FabricType.ResourceInfoProperty.ORG_NAMES));
    }

    private static void checkParams(Map<Object, Object> properties) throws Exception {
        checkParamsKeyExist(properties, FabricType.ResourceInfoProperty.CHANNEL_NAME);
        checkParamsKeyExist(properties, FabricType.ResourceInfoProperty.CHAINCODE_NAME);
        checkParamsKeyExist(properties, FabricType.ResourceInfoProperty.CHAINCODE_VERSION);
        checkParamsKeyExist(properties, FabricType.ResourceInfoProperty.PROPOSAL_WAIT_TIME);
        checkParamsKeyExist(properties, FabricType.ResourceInfoProperty.ORG_NAMES);
    }

    private static void checkParamsKeyExist(Map<Object, Object> properties, String key)
            throws Exception {
        if (!properties.containsKey(key)) {
            throw new Exception(key + " is not set");
        }
    }

    public String getChannelName() {
        return this.channelName;
    }

    public String getChaincodeName() {
        return chainCodeName;
    }

    public String getVersion() {
        return version;
    }

    public long getProposalWaitTime() {
        return proposalWaitTime;
    }

    public ArrayList<String> getOrgNames() {

        return orgNames;
    }

    public void check() throws Exception {
        if (this.channelName == null) {
            throw new Exception("channelName not set");
        }

        if (this.chainCodeName == null) {
            throw new Exception("chainCodeName not set");
        }

        if (this.chainCodeName == null) {
            throw new Exception("version not set");
        }

        if (this.proposalWaitTime == 0) {
            throw new Exception("proposalWaitTime not set");
        }

        if (this.orgNames == null) {
            throw new Exception("orgNames not set");
        }
    }
}
