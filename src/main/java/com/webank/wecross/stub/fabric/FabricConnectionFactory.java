package com.webank.wecross.stub.fabric;

import com.webank.wecross.account.FabricAccountFactory;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.Orderer;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.TransactionException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FabricConnectionFactory {
    private static Logger logger = LoggerFactory.getLogger(FabricConnectionFactory.class);

    public static FabricConnection build(String path) {
        String stubPath = path + File.separator + "stub.toml";
        try {
            FabricStubConfigFile configFile = new FabricStubConfigFile(stubPath);
            HFClient hfClient = buildClient(configFile);
            Map<String, Peer> peersMap = buildPeersMap(hfClient, configFile);
            Channel channel = buildChannel(hfClient, peersMap, configFile);
            Map<String, ChaincodeConnection> fabricChaincodeMap =
                    buildFabricChaincodeMap(hfClient, peersMap, channel, configFile);

            return new FabricConnection(hfClient, channel, fabricChaincodeMap);

        } catch (Exception e) {
            logger.error(e.getMessage());
            return null;
        }
    }

    public static HFClient buildClient(FabricStubConfigFile fabricStubConfigFile) throws Exception {
        HFClient hfClient = HFClient.createNewInstance();
        hfClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());

        User admin =
                FabricAccountFactory.buildUser(
                        fabricStubConfigFile.getFabricServices().getOrgUserName(),
                        fabricStubConfigFile.getFabricServices().getOrgUserAccountPath());
        hfClient.setUserContext(admin);
        return hfClient;
    }

    public static Map<String, Peer> buildPeersMap(
            HFClient client, FabricStubConfigFile fabricStubConfigFile) throws Exception {
        Map<String, Peer> peersMap = new HashMap<>();
        int index = 0;
        Map<String, FabricStubConfigFile.Peers.Peer> peersInfoMap = fabricStubConfigFile.getPeers();
        for (Map.Entry<String, FabricStubConfigFile.Peers.Peer> peerInfo :
                peersInfoMap.entrySet()) {
            String name = peerInfo.getKey();
            FabricStubConfigFile.Peers.Peer peer = peerInfo.getValue();
            peersMap.put(name, buildPeer(client, peer, index));
            index++;
        }
        return peersMap;
    }

    // Create Channel
    public static Channel buildChannel(
            HFClient client, Map<String, Peer> peersMap, FabricStubConfigFile fabricStubConfigFile)
            throws InvalidArgumentException, TransactionException {
        Orderer orderer1 = buildOrderer(client, fabricStubConfigFile);
        Channel channel =
                client.newChannel(fabricStubConfigFile.getFabricServices().getChannelName());
        channel.addOrderer(orderer1);

        for (Peer peer : peersMap.values()) {
            channel.addPeer(peer);
        }

        // channel.initialize(); not to start channel here
        return channel;
    }

    public static Map<String, ChaincodeConnection> buildFabricChaincodeMap(
            HFClient client,
            Map<String, Peer> peersMap,
            Channel channel,
            FabricStubConfigFile fabricStubConfigFile)
            throws Exception {
        Map<String, ChaincodeConnection> fabricChaincodeMap = new HashMap<>();

        List<FabricStubConfigFile.Resources.Resource> resourceList =
                fabricStubConfigFile.getResources();

        for (FabricStubConfigFile.Resources.Resource resourceObj : resourceList) {
            String name = resourceObj.getName();
            ChaincodeConnection chaincodeConnection =
                    new ChaincodeConnection(client, peersMap, channel, resourceObj);
            fabricChaincodeMap.put(name, chaincodeConnection);
        }
        return fabricChaincodeMap;
    }

    public static Orderer buildOrderer(HFClient client, FabricStubConfigFile fabricStubConfigFile)
            throws InvalidArgumentException {
        Properties orderer1Prop = new Properties();
        orderer1Prop.setProperty(
                "pemFile", fabricStubConfigFile.getFabricServices().getOrdererTlsCaFile());
        orderer1Prop.setProperty("sslProvider", "openSSL");
        orderer1Prop.setProperty("negotiationType", "TLS");
        orderer1Prop.setProperty("ordererWaitTimeMilliSecs", "300000");
        orderer1Prop.setProperty("hostnameOverride", "orderer");
        orderer1Prop.setProperty("trustServerCertificate", "true");
        orderer1Prop.setProperty("allowAllHostNames", "true");
        Orderer orderer =
                client.newOrderer(
                        "orderer",
                        fabricStubConfigFile.getFabricServices().getOrdererAddress(),
                        orderer1Prop);
        return orderer;
    }

    public static Peer buildPeer(
            HFClient client, FabricStubConfigFile.Peers.Peer peerConfig, Integer index)
            throws InvalidArgumentException {
        logger.info("getPeerTlsCaFile:{}", peerConfig.getPeerTlsCaFile());
        Properties peer0Prop = new Properties();
        peer0Prop.setProperty("pemFile", peerConfig.getPeerTlsCaFile());
        peer0Prop.setProperty("sslProvider", "openSSL");
        peer0Prop.setProperty("negotiationType", "TLS");
        peer0Prop.setProperty("hostnameOverride", "peer0");
        peer0Prop.setProperty("trustServerCertificate", "true");
        peer0Prop.setProperty("allowAllHostNames", "true");
        Peer peer = client.newPeer("peer" + index, peerConfig.getPeerAddress(), peer0Prop);
        return peer;
    }
}
