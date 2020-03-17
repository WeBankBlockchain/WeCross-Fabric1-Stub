package com.webank.wecross.stub.fabric;

import java.util.Collection;
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

    private HFClient hfClient;
    private Channel channel;
    private Collection<Peer> endorsers;

    public ChaincodeConnection(
            HFClient hfClient,
            Map<String, Peer> peersMap,
            Channel channel,
            FabricStubConfigFile.Resources.Resource resourceConfig) {
        this.name = resourceConfig.getName();
        this.type = resourceConfig.getType();
        this.chainCodeName = resourceConfig.getChainCodeName();
        this.chainLanguage = resourceConfig.getChainLanguage();
        this.chaincodeID = ChaincodeID.newBuilder().setName(this.chainCodeName).build();
        this.proposalWaitTime = resourceConfig.getProposalWaitTime();

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
}
