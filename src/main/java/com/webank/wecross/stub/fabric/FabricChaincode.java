package com.webank.wecross.stub.fabric;

import java.util.Collection;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.Peer;

public class FabricChaincode {
    private HFClient hfClient;
    private Channel channel;
    private Collection<Peer> endorser;

    private String name;
    private String type;
    private String chainCodeName;
    private String chainLanguage;
    private ChaincodeID chaincodeID;
    private long proposalWaitTime;

    public FabricChaincode(FabricStubConfigFile.Resources.Resource resourceConfig) {}

    public void setHfClient(HFClient hfClient) {
        this.hfClient = hfClient;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public void setEndorser(Collection<Peer> endorser) {
        this.endorser = endorser;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setChainCodeName(String chainCodeName) {
        this.chainCodeName = chainCodeName;
    }

    public void setChainLanguage(String chainLanguage) {
        this.chainLanguage = chainLanguage;
    }

    public void setChaincodeID(ChaincodeID chaincodeID) {
        this.chaincodeID = chaincodeID;
    }

    public void setProposalWaitTime(long proposalWaitTime) {
        this.proposalWaitTime = proposalWaitTime;
    }
}
