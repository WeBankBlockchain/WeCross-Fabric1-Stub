package com.webank.wecross.stub.fabric;

import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.utils.core.HashUtils;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.Peer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChaincodeConnection {
    private Logger logger = LoggerFactory.getLogger(ChaincodeConnection.class);

    private String name;
    private String type;
    private String chainCodeName;
    private String chainLanguage;
    private ChaincodeID chaincodeID;
    private long proposalWaitTime;
    private org.hyperledger.fabric.sdk.TransactionRequest.Type chainCodeType;

    private HFClient hfClient;
    private Channel channel;
    private Collection<Peer> endorsers;

    public ChaincodeConnection(
            HFClient hfClient,
            Map<String, Peer> peersMap,
            Channel channel,
            FabricStubConfigFile.Resources.Resource resourceConfig)
            throws Exception {
        this.name = resourceConfig.getName();
        this.type = resourceConfig.getType();
        this.chainCodeName = resourceConfig.getChainCodeName();
        this.chainLanguage = resourceConfig.getChainLanguage();
        this.chaincodeID = ChaincodeID.newBuilder().setName(this.chainCodeName).build();
        this.proposalWaitTime = resourceConfig.getProposalWaitTime();

        if (resourceConfig.getChainLanguage().toLowerCase().equals("go")) {
            this.chainCodeType = org.hyperledger.fabric.sdk.TransactionRequest.Type.GO_LANG;
        } else if (resourceConfig.getChainLanguage().toLowerCase().equals("java")) {
            this.chainCodeType = org.hyperledger.fabric.sdk.TransactionRequest.Type.JAVA;
        } else if (resourceConfig.getChainLanguage().toLowerCase().equals("node")) {
            this.chainCodeType = org.hyperledger.fabric.sdk.TransactionRequest.Type.NODE;
        } else {
            String errorMessage =
                    "\"chainLanguage\" in [[resource]] not support chaincode language "
                            + resourceConfig.getChainLanguage();
            throw new Exception(errorMessage);
        }

        this.hfClient = hfClient;
        this.channel = channel;

        this.endorsers = new LinkedHashSet<>();
        for (String endorserName : resourceConfig.getPeers()) {
            Peer endorser = peersMap.get(endorserName);
            if (endorser == null) {
                logger.warn("Could not found endorser " + endorserName + " in peersMap");
                continue;
            }

            endorsers.add(endorser);
        }
    }

    public ResourceInfo getResourceInfo() {
        ResourceInfo resourceInfo = new ResourceInfo();
        resourceInfo.setName(name);
        resourceInfo.setStubType(FabricType.RESOURCE_TYPE_FABRIC_CONTRACT);

        Map<Object, Object> properties = new HashMap<>();
        properties.put(FabricType.CHANNEL_NAME, channel.getName());
        properties.put(FabricType.CHAINCODE_NAME, chainCodeName);
        properties.put(FabricType.CHAINCODE_TYPE, chainCodeType);
        properties.put(FabricType.PROPOSAL_WAIT_TIME, proposalWaitTime);
        resourceInfo.setProperties(properties);

        resourceInfo.setChecksum(HashUtils.sha256String(chainCodeName));

        return resourceInfo;
    }
}
