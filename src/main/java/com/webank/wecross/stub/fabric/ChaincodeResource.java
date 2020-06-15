package com.webank.wecross.stub.fabric;

import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.utils.core.HashUtils;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import org.hyperledger.fabric.sdk.Peer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChaincodeResource {
    private Logger logger = LoggerFactory.getLogger(ChaincodeResource.class);

    private String name;
    private String chainCodeName;
    private long proposalWaitTime;
    private Collection<Peer> endorsers;
    private String channelName;

    public ChaincodeResource(
            Map<String, Peer> peersMap, FabricStubConfigParser.Resources.Resource resourceConfig, String channelName) {
        this.name = resourceConfig.getName();
        this.chainCodeName = resourceConfig.getChainCodeName();
        this.proposalWaitTime = resourceConfig.getProposalWaitTime();
        this.endorsers = new LinkedHashSet<>();
        this.channelName = channelName;
        for (String endorserName : resourceConfig.getPeers()) {
            Peer endorser = peersMap.get(endorserName);
            if (endorser == null) {
                logger.warn("Could not found endorser " + endorserName + " in peersMap");
                continue;
            }

            endorsers.add(endorser);
        }
    }

    public ChaincodeResource(String name, String chainCodeName, String channelName) {
        this.name = name;
        this.chainCodeName = chainCodeName;
        this.proposalWaitTime = FabricStubConfigParser.DEFAULT_PROPOSAL_WAIT_TIME;
        this.endorsers = new LinkedHashSet<>();
        this.channelName = channelName;
    }

    public ResourceInfo getResourceInfo() {
        ResourceInfo resourceInfo = new ResourceInfo();
        resourceInfo.setName(name);
        resourceInfo.setStubType(FabricType.STUB_NAME);

        resourceInfo.setProperties(
                ResourceInfoProperty.build()
                        .channelName(channelName)
                        .chainCodeName(chainCodeName)
                        .proposalWaitTime(proposalWaitTime)
                        .toMap());

        resourceInfo.setChecksum(HashUtils.sha256String(chainCodeName));

        return resourceInfo;
    }

    public Collection<Peer> getEndorsers() {
        return endorsers;
    }

    public void addEndorser(Peer endorser) {
        endorsers.add(endorser);
    }

    @Override
    public String toString() {
        return "ChaincodeResource{"
                + "name='"
                + name
                + '\''
                + ", chainCodeName='"
                + chainCodeName
                + '\''
                + ", proposalWaitTime="
                + proposalWaitTime
                + ", endorsers="
                + endorsers
                + '}';
    }

    public String getName() {
        return name;
    }

    public String getChainCodeName() {
        return chainCodeName;
    }

    public long getProposalWaitTime() {
        return proposalWaitTime;
    }
}
