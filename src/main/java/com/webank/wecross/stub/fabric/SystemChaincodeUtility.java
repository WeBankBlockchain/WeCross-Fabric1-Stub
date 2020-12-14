package com.webank.wecross.stub.fabric;

import com.webank.wecross.stub.*;
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstallChaincodeRequest;
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstantiateChaincodeRequest;
import com.webank.wecross.stub.fabric.FabricCustomCommand.UpgradeChaincodeRequest;
import com.webank.wecross.utils.TarUtils;
import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class SystemChaincodeUtility {
    public static final int Proxy = 0;
    public static final int Hub = 1;

    public static void deploy(String chainPath, int type, String chaincodeName, String[] args)
            throws Exception {
        String stubPath = "classpath:" + File.separator + chainPath;

        FabricStubConfigParser configFile = new FabricStubConfigParser(stubPath);
        String version = String.valueOf(System.currentTimeMillis() / 1000);
        FabricConnection connection = FabricConnectionFactory.build(stubPath);
        connection.start();

        if (type == Proxy) {
            if (connection.hasProxyDeployed2AllPeers()) {
                System.out.println("SUCCESS: WeCrossProxy has been deployed to all connected org");
                return;
            }
        } else if (type == Hub) {
            if (connection.hasHubDeployed2AllPeers()) {
                System.out.println("SUCCESS: WeCrossHub has been deployed to all connected org");
                return;
            }
        } else {
            System.out.println("ERROR: type " + type + " not supported");
            return;
        }

        FabricStubFactory fabricStubFactory = new FabricStubFactory();
        Driver driver = fabricStubFactory.newDriver();
        BlockManager blockHeaderManager = new DirectBlockManager(driver, connection);
        List<String> orgNames = new LinkedList<>();
        String adminName = configFile.getFabricServices().getOrgUserName();
        Account admin =
                fabricStubFactory.newAccount(
                        adminName, "classpath:accounts" + File.separator + adminName);

        for (Map.Entry<String, FabricStubConfigParser.Orgs.Org> orgEntry :
                configFile.getOrgs().entrySet()) {
            String orgName = orgEntry.getKey();
            orgNames.add(orgName);
            String accountName = orgEntry.getValue().getAdminName();

            Account orgAdmin =
                    fabricStubFactory.newAccount(
                            accountName, "classpath:accounts" + File.separator + accountName);

            String chaincodeFilesDir =
                    "classpath:"
                            + chainPath
                            + File.separator
                            + "chaincode/"
                            + chaincodeName
                            + File.separator;
            byte[] code = TarUtils.generateTarGzInputStreamBytesFoGoChaincode(chaincodeFilesDir);
            install(
                    orgName,
                    connection,
                    driver,
                    orgAdmin,
                    blockHeaderManager,
                    chaincodeName,
                    code,
                    version);
        }
        instantiate(
                orgNames,
                connection,
                driver,
                admin,
                blockHeaderManager,
                chaincodeName,
                args,
                version);
        System.out.println("SUCCESS: " + chaincodeName + " has been deployed to " + chainPath);
    }

    public static void upgrade(String chainPath, String chaincodeName, String[] args)
            throws Exception {
        String stubPath = "classpath:" + File.separator + chainPath;

        FabricStubConfigParser configFile = new FabricStubConfigParser(stubPath);
        String version = String.valueOf(System.currentTimeMillis() / 1000);
        FabricConnection connection = FabricConnectionFactory.build(stubPath);
        connection.start();

        FabricStubFactory fabricStubFactory = new FabricStubFactory();
        Driver driver = fabricStubFactory.newDriver();
        BlockManager blockManager = new DirectBlockManager(driver, connection);
        List<String> orgNames = new LinkedList<>();
        String adminName = configFile.getFabricServices().getOrgUserName();
        Account admin =
                fabricStubFactory.newAccount(
                        adminName, "classpath:accounts" + File.separator + adminName);

        for (Map.Entry<String, FabricStubConfigParser.Orgs.Org> orgEntry :
                configFile.getOrgs().entrySet()) {
            String orgName = orgEntry.getKey();
            orgNames.add(orgName);
            String accountName = orgEntry.getValue().getAdminName();

            Account orgAdmin =
                    fabricStubFactory.newAccount(
                            accountName, "classpath:accounts" + File.separator + accountName);

            String chaincodeFilesDir =
                    "classpath:"
                            + chainPath
                            + File.separator
                            + "chaincode/"
                            + chaincodeName
                            + File.separator;
            byte[] code = TarUtils.generateTarGzInputStreamBytesFoGoChaincode(chaincodeFilesDir);
            install(
                    orgName,
                    connection,
                    driver,
                    orgAdmin,
                    blockManager,
                    chaincodeName,
                    code,
                    version);
        }
        upgrade(orgNames, connection, driver, admin, blockManager, chaincodeName, args, version);
        System.out.println("SUCCESS: " + chaincodeName + " has been upgraded to " + chainPath);
    }

    private static void install(
            String orgName,
            FabricConnection connection,
            Driver driver,
            Account user,
            BlockManager blockManager,
            String chaincodeName,
            byte[] code,
            String version)
            throws Exception {
        System.out.println("Install " + chaincodeName + ":" + version + " to " + orgName + " ...");

        String channelName = connection.getChannel().getName();
        String language = "GO_LANG";

        InstallChaincodeRequest installChaincodeRequest =
                InstallChaincodeRequest.build()
                        .setName(chaincodeName)
                        .setVersion(version)
                        .setOrgName(orgName)
                        .setChannelName(channelName)
                        .setChaincodeLanguage(language)
                        .setCode(code);

        TransactionContext transactionContext =
                new TransactionContext(user, null, null, blockManager);

        CompletableFuture<TransactionException> future1 = new CompletableFuture<>();
        ((FabricDriver) driver)
                .asyncInstallChaincode(
                        transactionContext,
                        installChaincodeRequest,
                        connection,
                        (transactionException, transactionResponse) ->
                                future1.complete(transactionException));

        TransactionException e1 = future1.get(80, TimeUnit.SECONDS);
        if (!e1.isSuccess()) {
            System.out.println("WARNING: asyncCustomCommand install: " + e1.getMessage());
        }
    }

    private static void instantiate(
            List<String> orgNames,
            FabricConnection connection,
            Driver driver,
            Account user,
            BlockManager blockManager,
            String chaincodeName,
            String[] args,
            String version)
            throws Exception {
        System.out.println(
                "Instantiating "
                        + chaincodeName
                        + ":"
                        + version
                        + " to "
                        + orgNames.toString()
                        + " ...");
        String channelName = connection.getChannel().getName();
        String language = "GO_LANG";

        InstantiateChaincodeRequest instantiateChaincodeRequest =
                InstantiateChaincodeRequest.build()
                        .setName(chaincodeName)
                        .setVersion(version)
                        .setOrgNames(orgNames.toArray(new String[] {}))
                        .setChannelName(channelName)
                        .setChaincodeLanguage(language)
                        .setEndorsementPolicy("") // "OR ('Org1MSP.peer','Org2MSP.peer')"
                        .setArgs(args);

        TransactionContext transactionContext =
                new TransactionContext(user, null, null, blockManager);

        CompletableFuture<TransactionException> future2 = new CompletableFuture<>();
        ((FabricDriver) driver)
                .asyncInstantiateChaincode(
                        transactionContext,
                        instantiateChaincodeRequest,
                        connection,
                        (transactionException, transactionResponse) ->
                                future2.complete(transactionException));

        TransactionException e2 = future2.get(180, TimeUnit.SECONDS);
        if (!e2.isSuccess()) {
            throw new Exception("ERROR: asyncCustomCommand instantiate error: " + e2.getMessage());
        }
    }

    private static void upgrade(
            List<String> orgNames,
            FabricConnection connection,
            Driver driver,
            Account user,
            BlockManager blockManager,
            String chaincodeName,
            String[] args,
            String version)
            throws Exception {
        System.out.println(
                "Upgrade " + chaincodeName + ":" + version + " to " + orgNames.toString() + " ...");
        String channelName = connection.getChannel().getName();
        String language = "GO_LANG";

        UpgradeChaincodeRequest upgradeChaincodeRequest =
                UpgradeChaincodeRequest.build()
                        .setName(chaincodeName)
                        .setVersion(version)
                        .setOrgNames(orgNames.toArray(new String[] {}))
                        .setChannelName(channelName)
                        .setChaincodeLanguage(language)
                        .setEndorsementPolicy("") // "OR ('Org1MSP.peer','Org2MSP.peer')"
                        // .setTransientMap()
                        .setArgs(args);
        TransactionContext transactionContext =
                new TransactionContext(user, null, null, blockManager);

        CompletableFuture<TransactionException> future2 = new CompletableFuture<>();
        ((FabricDriver) driver)
                .asyncUpgradeChaincode(
                        transactionContext,
                        upgradeChaincodeRequest,
                        connection,
                        (transactionException, transactionResponse) ->
                                future2.complete(transactionException));

        TransactionException e2 = future2.get(180, TimeUnit.SECONDS);
        if (!e2.isSuccess()) {
            throw new Exception("ERROR: asyncCustomCommand upgrade error: " + e2.getMessage());
        }
    }

    public static class DirectBlockManager implements BlockManager {
        private Driver driver;
        private Connection connection;

        public DirectBlockManager(Driver driver, Connection connection) {
            this.driver = driver;
            this.connection = connection;
        }

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        public void asyncGetBlockNumber(GetBlockNumberCallback callback) {
            driver.asyncGetBlockNumber(
                    connection, (e, blockNumber) -> callback.onResponse(e, blockNumber));
        }

        @Override
        public void asyncGetBlock(long blockNumber, GetBlockCallback callback) {
            driver.asyncGetBlock(
                    blockNumber, true, connection, (e, block) -> callback.onResponse(e, block));
        }
    }
}
