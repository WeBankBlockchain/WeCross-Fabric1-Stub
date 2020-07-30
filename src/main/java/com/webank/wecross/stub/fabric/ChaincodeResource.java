package com.webank.wecross.stub.fabric;

import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.utils.HashUtils;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
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
                        .orgNames(getOrgNames())
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

    public String[] getOrgNames() {
        Set<String> orgNameSet = new HashSet<>();
        for (Peer peer : endorsers) {
            orgNameSet.add((String) peer.getProperties().getProperty(FabricType.ORG_NAME_DEF));
        }
        String[] orgNames = new String[] {};
        orgNames = orgNameSet.toArray(orgNames);
        return orgNames;
    }
}
