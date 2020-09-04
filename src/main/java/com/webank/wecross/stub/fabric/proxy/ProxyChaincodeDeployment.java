package com.webank.wecross.stub.fabric.proxy;

import com.webank.wecross.stub.Account;
import com.webank.wecross.stub.Block;
import com.webank.wecross.stub.BlockHeaderManager;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.TransactionContext;
import com.webank.wecross.stub.TransactionException;
import com.webank.wecross.stub.TransactionResponse;
import com.webank.wecross.stub.fabric.FabricConnection;
import com.webank.wecross.stub.fabric.FabricConnectionFactory;
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstallChaincodeRequest;
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstantiateChaincodeRequest;
import com.webank.wecross.stub.fabric.FabricCustomCommand.UpgradeChaincodeRequest;
import com.webank.wecross.stub.fabric.FabricDriver;
import com.webank.wecross.stub.fabric.FabricStubConfigParser;
import com.webank.wecross.stub.fabric.FabricStubFactory;
import com.webank.wecross.utils.TarUtils;
import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ProxyChaincodeDeployment {

    public static void usage() {
        System.out.println(getUsage("chains/fabric"));
        exit();
    }

    public static String getUsage(String chainPath) {
        String pureChainPath = chainPath.replace("classpath:/", "").replace("classpath:", "");
        return "Usage:\n"
                + "         java -cp 'conf/:lib/*:plugin/*' "
                + ProxyChaincodeDeployment.class.getName()
                + " check [chainName]\n"
                + "         java -cp 'conf/:lib/*:plugin/*' "
                + ProxyChaincodeDeployment.class.getName()
                + " deploy [chainName]\n"
                + "         java -cp 'conf/:lib/*:plugin/*' "
                + ProxyChaincodeDeployment.class.getName()
                + " upgrade [chainName]\n"
                + "Example:\n"
                + "         java -cp 'conf/:lib/*:plugin/*' "
                + ProxyChaincodeDeployment.class.getName()
                + " check "
                + pureChainPath
                + "\n"
                + "         java -cp 'conf/:lib/*:plugin/*' "
                + ProxyChaincodeDeployment.class.getName()
                + " deploy "
                + pureChainPath
                + "\n"
                + "         java -cp 'conf/:lib/*:plugin/*' "
                + ProxyChaincodeDeployment.class.getName()
                + " upgrade "
                + pureChainPath
                + "";
    }

    private static void exit() {
        System.exit(0);
    }

    private static void exit(int sig) {
        System.exit(sig);
    }

    public static void check(String chainPath) throws Exception {
        String stubPath = "classpath:" + File.separator + chainPath;
        FabricStubFactory fabricStubFactory = new FabricStubFactory();
        FabricConnection connection = (FabricConnection) fabricStubFactory.newConnection(stubPath);

        if (connection != null) {
            System.out.println("SUCCESS: WeCrossProxy has been deployed to all connected org");
        }
    }

    public static void deploy(String chainPath) throws Exception {
        String stubPath = "classpath:" + File.separator + chainPath;

        FabricStubConfigParser configFile = new FabricStubConfigParser(stubPath);
        String version = String.valueOf(System.currentTimeMillis() / 1000);
        FabricConnection connection = FabricConnectionFactory.build(stubPath);
        connection.start();

        // Check proxy chaincode
        if (connection.hasProxyDeployed2AllPeers()) {
            System.out.println("SUCCESS: WeCrossProxy has been deployed to all connected org");
        } else {

            FabricStubFactory fabricStubFactory = new FabricStubFactory();
            Driver driver = fabricStubFactory.newDriver();
            BlockHeaderManager blockHeaderManager =
                    new DirectBlockHeaderManager(driver, connection);
            List<String> orgNames = new LinkedList<>();
            String adminName = configFile.getFabricServices().getOrgUserName();
            Account admin =
                    fabricStubFactory.newAccount(
                            adminName, "classpath:accounts" + File.separator + adminName);
            String chaincodeName = configFile.getAdvanced().getProxyChaincode();

            for (Map.Entry<String, FabricStubConfigParser.Orgs.Org> orgEntry :
                    configFile.getOrgs().entrySet()) {
                String orgName = orgEntry.getKey();
                orgNames.add(orgName);
                String accountName = orgEntry.getValue().getAdminName();

                Account orgAdmin =
                        fabricStubFactory.newAccount(
                                accountName, "classpath:accounts" + File.separator + accountName);

                String chaincodeFilesDir =
                        "classpath:" + chainPath + File.separator + chaincodeName + File.separator;
                byte[] code =
                        TarUtils.generateTarGzInputStreamBytesFoGoChaincode(
                                chaincodeFilesDir); // Proxy is go
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
                    version);
            System.out.println("SUCCESS: " + chaincodeName + " has been deployed to " + chainPath);
        }
    }

    public static void upgrade(String chainPath) throws Exception {
        String stubPath = "classpath:" + File.separator + chainPath;

        FabricStubConfigParser configFile = new FabricStubConfigParser(stubPath);
        String version = String.valueOf(System.currentTimeMillis() / 1000);
        FabricConnection connection = FabricConnectionFactory.build(stubPath);
        connection.start();

        FabricStubFactory fabricStubFactory = new FabricStubFactory();
        Driver driver = fabricStubFactory.newDriver();
        BlockHeaderManager blockHeaderManager = new DirectBlockHeaderManager(driver, connection);
        List<String> orgNames = new LinkedList<>();
        String adminName = configFile.getFabricServices().getOrgUserName();
        Account admin =
                fabricStubFactory.newAccount(
                        adminName, "classpath:accounts" + File.separator + adminName);
        String chaincodeName = configFile.getAdvanced().getProxyChaincode();

        for (Map.Entry<String, FabricStubConfigParser.Orgs.Org> orgEntry :
                configFile.getOrgs().entrySet()) {
            String orgName = orgEntry.getKey();
            orgNames.add(orgName);
            String accountName = orgEntry.getValue().getAdminName();

            Account orgAdmin =
                    fabricStubFactory.newAccount(
                            accountName, "classpath:accounts" + File.separator + accountName);

            String chaincodeFilesDir =
                    "classpath:" + chainPath + File.separator + chaincodeName + File.separator;
            byte[] code =
                    TarUtils.generateTarGzInputStreamBytesFoGoChaincode(
                            chaincodeFilesDir); // Proxy is go
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
        upgrade(orgNames, connection, driver, admin, blockHeaderManager, chaincodeName, version);
        System.out.println("SUCCESS: " + chaincodeName + " has been upgraded to " + chainPath);
    }

    public static void install(
            String orgName,
            FabricConnection connection,
            Driver driver,
            Account user,
            BlockHeaderManager blockHeaderManager,
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
                new TransactionContext(user, null, null, blockHeaderManager);

        CompletableFuture<TransactionException> future1 = new CompletableFuture<>();
        ((FabricDriver) driver)
                .asyncInstallChaincode(
                        transactionContext,
                        installChaincodeRequest,
                        connection,
                        new Driver.Callback() {
                            @Override
                            public void onTransactionResponse(
                                    TransactionException transactionException,
                                    TransactionResponse transactionResponse) {
                                future1.complete(transactionException);
                            }
                        });

        TransactionException e1 = future1.get(80, TimeUnit.SECONDS);
        if (!e1.isSuccess()) {
            System.out.println("WARNING: asyncCustomCommand install: " + e1.getMessage());
        }
    }

    public static void instantiate(
            List<String> orgNames,
            FabricConnection connection,
            Driver driver,
            Account user,
            BlockHeaderManager blockHeaderManager,
            String chaincodeName,
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

        String[] args = new String[] {channelName};

        InstantiateChaincodeRequest instantiateChaincodeRequest =
                InstantiateChaincodeRequest.build()
                        .setName(chaincodeName)
                        .setVersion(version)
                        .setOrgNames(orgNames.toArray(new String[] {}))
                        .setChannelName(channelName)
                        .setChaincodeLanguage(language)
                        .setEndorsementPolicy("") // "OR ('Org1MSP.peer','Org2MSP.peer')"
                        // .setTransientMap()
                        .setArgs(args);

        TransactionContext transactionContext =
                new TransactionContext(user, null, null, blockHeaderManager);

        CompletableFuture<TransactionException> future2 = new CompletableFuture<>();
        ((FabricDriver) driver)
                .asyncInstantiateChaincode(
                        transactionContext,
                        instantiateChaincodeRequest,
                        connection,
                        new Driver.Callback() {
                            @Override
                            public void onTransactionResponse(
                                    TransactionException transactionException,
                                    TransactionResponse transactionResponse) {
                                future2.complete(transactionException);
                            }
                        });

        TransactionException e2 = future2.get(50, TimeUnit.SECONDS);
        if (!e2.isSuccess()) {
            throw new Exception("ERROR: asyncCustomCommand instantiate error: " + e2.getMessage());
        }
    }

    public static void upgrade(
            List<String> orgNames,
            FabricConnection connection,
            Driver driver,
            Account user,
            BlockHeaderManager blockHeaderManager,
            String chaincodeName,
            String version)
            throws Exception {
        System.out.println(
                "Upgrade " + chaincodeName + ":" + version + " to " + orgNames.toString() + " ...");
        String channelName = connection.getChannel().getName();
        String language = "GO_LANG";

        String[] args = new String[] {channelName};

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
                new TransactionContext(user, null, null, blockHeaderManager);

        CompletableFuture<TransactionException> future2 = new CompletableFuture<>();
        ((FabricDriver) driver)
                .asyncUpgradeChaincode(
                        transactionContext,
                        upgradeChaincodeRequest,
                        connection,
                        new Driver.Callback() {
                            @Override
                            public void onTransactionResponse(
                                    TransactionException transactionException,
                                    TransactionResponse transactionResponse) {
                                future2.complete(transactionException);
                            }
                        });

        TransactionException e2 = future2.get(50, TimeUnit.SECONDS);
        if (!e2.isSuccess()) {
            throw new Exception("ERROR: asyncCustomCommand upgrade error: " + e2.getMessage());
        }
    }

    public static boolean hasInstantiate(String chainPath) throws Exception {
        String stubPath = "classpath:" + File.separator + chainPath;

        FabricStubConfigParser configFile = new FabricStubConfigParser(stubPath);
        String version = String.valueOf(System.currentTimeMillis() / 1000);
        FabricConnection connection = FabricConnectionFactory.build(stubPath);
        connection.start();

        Set<String> orgNames = configFile.getOrgs().keySet();
        Set<String> chainOrgNames = connection.getProxyOrgNames(true);

        orgNames.removeAll(chainOrgNames);
        return orgNames.isEmpty();
    }

    public static void main(String[] args) throws Exception {
        try {
            switch (args.length) {
                case 2:
                    handle2Args(args);
                    break;
                default:
                    usage();
            }
        } catch (Exception e) {
            System.out.println(e);
        } finally {
            exit();
        }
    }

    public static void handle2Args(String[] args) throws Exception {
        if (args.length != 2) {
            usage();
        }

        String cmd = args[0];
        String chainPath = args[1];

        switch (cmd) {
            case "check":
                check(chainPath);
                break;
            case "deploy":
                deploy(chainPath);
                break;
            case "upgrade":
                upgrade(chainPath);
                break;
            default:
                usage();
        }
    }

    public static class DirectBlockHeaderManager implements BlockHeaderManager {
        private Driver driver;
        private Connection connection;

        public DirectBlockHeaderManager(Driver driver, Connection connection) {
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
                    connection,
                    new Driver.GetBlockNumberCallback() {
                        @Override
                        public void onResponse(Exception e, long blockNumber) {
                            callback.onResponse(e, blockNumber);
                        }
                    });
        }

        @Override
        public void asyncGetBlockHeader(long blockNumber, GetBlockHeaderCallback callback) {
            driver.asyncGetBlock(
                    blockNumber,
                    true,
                    connection,
                    new Driver.GetBlockCallback() {
                        @Override
                        public void onResponse(Exception e, Block block) {
                            callback.onResponse(e, block.getBlockHeader());
                        }
                    });
        }
    }
}
