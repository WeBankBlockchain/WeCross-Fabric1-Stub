package com.webank.wecross.stub.fabric;

import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.ResourceInfo;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import org.hyperledger.fabric.protos.peer.Query;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.ChaincodeResponse;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.TransactionProposalRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChaincodeResourceManager {
    private static Logger logger = LoggerFactory.getLogger(ChaincodeResourceManager.class);

    private static final long updateChaincodeMapExpires = 10000; // ms

    public interface EventHandler {
        void onChange(List<ResourceInfo> resourceInfos);
    }

    private HFClient hfClient;
    private Channel channel;
    private Map<String, Peer> peersMap;
    private Map<String, ChaincodeResource> chaincodeMap = new HashMap<>();
    private Timer mainloopTimer;
    private EventHandler eventHandler;

    public ChaincodeResourceManager(
            HFClient hfClient, Channel channel, Map<String, Peer> peersMap) {
        this(hfClient, channel, peersMap, new HashMap<>());
    }

    public ChaincodeResourceManager(
            HFClient hfClient,
            Channel channel,
            Map<String, Peer> peersMap,
            Map<String, ChaincodeResource> chaincodeMap) {
        this.hfClient = hfClient;
        this.channel = channel;
        this.peersMap = peersMap;
        this.chaincodeMap = chaincodeMap;
    }

    public void start() {
        mainloopTimer = new Timer("ChaincodeResourceManager");
        mainloopTimer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        updateChaincodeMap();
                    }
                },
                0,
                updateChaincodeMapExpires);
    }

    public void setEventHandler(EventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    public ChaincodeResource getChaincodeResource(String name) {
        return chaincodeMap.get(name);
    }

    public List<ResourceInfo> getResourceInfoList() {
        List<ResourceInfo> resourceInfoList = new LinkedList<>();
        for (ChaincodeResource chaincodeResource : chaincodeMap.values()) {
            ResourceInfo resourceInfo = chaincodeResource.getResourceInfo();
            resourceInfo
                    .getProperties()
                    .put(FabricType.ResourceInfoProperty.CHANNEL_NAME, channel.getName());
            resourceInfoList.add(resourceInfo);
        }
        return resourceInfoList;
    }

    public Map<String, ChaincodeResource> getChaincodeMap() {
        return chaincodeMap;
    }

    private Map<String, ChaincodeResource> queryChaincodeMap() {
        Set<String> chaincodes = queryActiveChaincode();
        Map<String, ChaincodeResource> currentChaincodeMap = new HashMap<>();
        for (String chaincodeName : chaincodes) {
            for (Peer peer : peersMap.values()) {
                if (isChaincodeActiveInPeer(peer, chaincodeName)) {
                    if (currentChaincodeMap.get(chaincodeName) == null) {
                        currentChaincodeMap.put(
                                chaincodeName,
                                new ChaincodeResource(
                                        chaincodeName, chaincodeName, channel.getName()));
                    }
                    currentChaincodeMap.get(chaincodeName).addEndorser(peer);
                }
            }
        }
        return currentChaincodeMap;
    }

    private boolean isChaincodeActiveInPeer(Peer peer, String chaincodeName) {
        TransactionProposalRequest transactionProposalRequest =
                hfClient.newTransactionProposalRequest();
        transactionProposalRequest.setFcn("wecross_test_chaincode_active_probe");
        transactionProposalRequest.setChaincodeID(
                ChaincodeID.newBuilder().setName(chaincodeName).build());

        Collection<Peer> peers = new HashSet<>();
        peers.add(peer);
        try {
            Collection<ProposalResponse> responses =
                    channel.sendTransactionProposal(transactionProposalRequest, peers);
            return isChaincodeActive(responses);

        } catch (Exception e) {
            logger.debug(
                    "isChaincodeActiveInPeer peer:{}, doesn't have chaincode:{} expcetion:{}",
                    peer,
                    chaincodeName,
                    e);
            return false;
        }
    }

    private boolean isChaincodeActive(Collection<ProposalResponse> responses) {
        // cannot retrieve package for chaincode
        boolean isActive = true;
        for (ProposalResponse response : responses) {
            if (!response.getStatus().equals(ChaincodeResponse.Status.SUCCESS)
                    && response.getMessage().contains("cannot retrieve package for chaincode")) {
                isActive &= false;
            }
        }
        return isActive;
    }

    private Set<String> queryActiveChaincode() {
        Set<String> names = new HashSet<>();
        for (Peer peer : peersMap.values()) {
            try {
                List<Query.ChaincodeInfo> chaincodeInfos =
                        channel.queryInstantiatedChaincodes(peer);
                chaincodeInfos.forEach(chaincodeInfo -> names.add(chaincodeInfo.getName()));
            } catch (Exception e) {
                logger.warn("Could not get instantiated Chaincodes from:{} ", peer.toString());
            }
        }
        return names;
    }

    public void dumpChaincodeMap() {
        String output = "Chaincode Resources: ";
        for (Map.Entry<String, ChaincodeResource> entry : chaincodeMap.entrySet()) {
            output += "Name:" + entry.getKey() + " Resource:" + entry.getValue().toString() + "\n";
        }
        logger.debug(output);
    }

    public void updateChaincodeMap() {
        synchronized (this) {
            Map<String, ChaincodeResource> oldMap = this.chaincodeMap;
            this.chaincodeMap = queryChaincodeMap();

            if (eventHandler != null && !isSameChaincodeMap(oldMap, this.chaincodeMap)) {
                logger.info("Chaincode resource has changed to: {}", this.chaincodeMap.keySet());
                eventHandler.onChange(getResourceInfoList());
            }

            dumpChaincodeMap();
        }
    }

    private boolean isSameChaincodeMap(
            Map<String, ChaincodeResource> mp1, Map<String, ChaincodeResource> mp2) {
        return mp1.keySet().equals(mp2.keySet());
    }
}
