package com.webank.wecross.stub.fabric;

import com.webank.wecross.account.FabricAccountFactory;
import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.StubConstant;
import java.io.File;
import java.util.LinkedHashMap;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class FabricConnectionFactory {
    private static Logger logger = LoggerFactory.getLogger(FabricConnectionFactory.class);

    public static FabricConnection build(String stubPath) {
        try {
            FabricStubConfigParser configFile = new FabricStubConfigParser(stubPath);
            return build(configFile);

        } catch (Exception e) {
            Logger logger = LoggerFactory.getLogger(FabricConnectionFactory.class);
            logger.error("FabricConnection build exception: " + e);
            return null;
        }
    }

    public static FabricConnection build(Map<String, Object> config) {
        try {
            FabricStubConfigParser configFile = new FabricStubConfigParser(config);

            return build(configFile);

        } catch (Exception e) {
            Logger logger = LoggerFactory.getLogger(FabricConnectionFactory.class);
            logger.error("FabricConnection build exception: " + e);
            return null;
        }
    }

    public static FabricConnection build(FabricStubConfigParser configFile) throws Exception {

        HFClient hfClient = buildClient(configFile);
        Map<String, Peer> peersMap = buildPeersMap(hfClient, configFile);
        Channel channel = buildChannel(hfClient, peersMap, configFile);
        ThreadPoolTaskExecutor threadPool = buildThreadPool(configFile);

        return new FabricConnection(
                hfClient, channel, peersMap, StubConstant.PROXY_NAME, threadPool);
    }

    private static ThreadPoolTaskExecutor buildThreadPool(FabricStubConfigParser configFile) {
        ThreadPoolTaskExecutor threadPool = new ThreadPoolTaskExecutor();
        int corePoolSize = configFile.getAdvanced().getThreadPool().getCorePoolSize();
        int maxPoolSize = configFile.getAdvanced().getThreadPool().getMaxPoolSize();
        int queueCapacity = configFile.getAdvanced().getThreadPool().getQueueCapacity();
        threadPool.setCorePoolSize(corePoolSize);
        threadPool.setMaxPoolSize(maxPoolSize);
        threadPool.setQueueCapacity(queueCapacity);
        threadPool.setThreadNamePrefix("FabricConnection-");
        logger.info(
                "Init threadPool with corePoolSize:{}, maxPoolSize:{}, queueCapacity:{}",
                corePoolSize,
                maxPoolSize,
                queueCapacity);
        return threadPool;
    }

    public static HFClient buildClient(FabricStubConfigParser fabricStubConfigParser)
            throws Exception {
        HFClient hfClient = HFClient.createNewInstance();
        hfClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());

        String orgUserName = fabricStubConfigParser.getFabricServices().getOrgUserName();
        User admin =
                FabricAccountFactory.buildUser(
                        orgUserName, "classpath:accounts" + File.separator + orgUserName);
        hfClient.setUserContext(admin);
        return hfClient;
    }

    public static Map<String, Peer> buildPeersMap(
            HFClient client, FabricStubConfigParser fabricStubConfigParser) throws Exception {
        Map<String, Peer> peersMap = new LinkedHashMap<>();
        int index = 0;
        Map<String, FabricStubConfigParser.Orgs.Org> orgs = fabricStubConfigParser.getOrgs();

        for (Map.Entry<String, FabricStubConfigParser.Orgs.Org> orgEntry : orgs.entrySet()) {
            String orgName = orgEntry.getKey();
            FabricStubConfigParser.Orgs.Org org = orgEntry.getValue();

            for (String peerAddress : org.getEndorsers()) {
                String name = "peer-" + String.valueOf(index);
                peersMap.put(
                        name, buildPeer(client, peerAddress, org.getTlsCaFile(), orgName, index));
                index++;
            }
        }

        return peersMap;
    }

    // Create Channel
    public static Channel buildChannel(
            HFClient client,
            Map<String, Peer> peersMap,
            FabricStubConfigParser fabricStubConfigParser)
            throws InvalidArgumentException, TransactionException {
        Orderer orderer1 = buildOrderer(client, fabricStubConfigParser);
        Channel channel =
                client.newChannel(fabricStubConfigParser.getFabricServices().getChannelName());
        channel.addOrderer(orderer1);

        for (Peer peer : peersMap.values()) {
            channel.addPeer(peer);
        }

        // channel.initialize(); not to start channel here
        return channel;
    }

    public static Orderer buildOrderer(
            HFClient client, FabricStubConfigParser fabricStubConfigParser)
            throws InvalidArgumentException {
        Properties orderer1Prop = new Properties();
        orderer1Prop.setProperty(
                "pemFile", fabricStubConfigParser.getFabricServices().getOrdererTlsCaFile());
        // orderer1Prop.setProperty("sslProvider", "openSSL");
        orderer1Prop.setProperty("sslProvider", "JDK");
        orderer1Prop.setProperty("negotiationType", "TLS");
        orderer1Prop.setProperty("ordererWaitTimeMilliSecs", "300000");
        orderer1Prop.setProperty("hostnameOverride", "orderer");
        orderer1Prop.setProperty("trustServerCertificate", "true");
        orderer1Prop.setProperty("allowAllHostNames", "true");
        Orderer orderer =
                client.newOrderer(
                        "orderer",
                        fabricStubConfigParser.getFabricServices().getOrdererAddress(),
                        orderer1Prop);
        return orderer;
    }

    public static Peer buildPeer(
            HFClient client, String address, String tlsCaFile, String orgName, Integer index)
            throws InvalidArgumentException {
        Properties peer0Prop = new Properties();
        peer0Prop.setProperty("pemFile", tlsCaFile);
        // peer0Prop.setProperty("sslProvider", "openSSL");
        peer0Prop.setProperty("sslProvider", "JDK");
        peer0Prop.setProperty("negotiationType", "TLS");
        peer0Prop.setProperty("hostnameOverride", "peer0");
        peer0Prop.setProperty("trustServerCertificate", "true");
        peer0Prop.setProperty("allowAllHostNames", "true");
        peer0Prop.setProperty(
                FabricType.ORG_NAME_DEF, orgName); // ORG_NAME_DEF is only used by wecross
        Peer peer = client.newPeer("peer" + index, address, peer0Prop);
        return peer;
    }
}
