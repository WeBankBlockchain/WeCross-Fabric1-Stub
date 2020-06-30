package com.webank.wecross.stub.fabric.proxy;

import com.webank.wecross.stub.Account;
import com.webank.wecross.stub.BlockHeaderManager;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.fabric.FabricConnection;
import com.webank.wecross.stub.fabric.FabricConnectionFactory;
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstallCommand;
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstantiateCommand;
import com.webank.wecross.stub.fabric.FabricStubConfigParser;
import com.webank.wecross.stub.fabric.FabricStubFactory;
import com.webank.wecross.utils.TarUtils;
import java.io.File;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ProxyChaincodeDeployment {
    public static String USAGE =
            "Usage:\n"
                    + "         java -cp conf/:lib/*:plugin/* "
                    + ProxyChaincodeDeployment.class.getName()
                    + " check [chainName]\n"
                    + "         java -cp conf/:lib/*:plugin/* "
                    + ProxyChaincodeDeployment.class.getName()
                    + " deploy [chainName] [accountName] [orgName]\n"
                    + "Example:\n"
                    + "         java -cp conf/:lib/*:plugin/* "
                    + ProxyChaincodeDeployment.class.getName()
                    + " check chains/fabric\n"
                    + "         java -cp conf/:lib/*:plugin/* "
                    + ProxyChaincodeDeployment.class.getName()
                    + " deploy chains/fabric fabric_admin_org1 Org1\n"
                    + "         java -cp conf/:lib/*:plugin/* "
                    + ProxyChaincodeDeployment.class.getName()
                    + " deploy chains/fabric fabric_admin_org2 Org2";

    public static void usage() {
        System.out.println(USAGE);
        exit();
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

    public static void deploy(String chainPath, String accountName, String orgName)
            throws Exception {
        String stubPath = "classpath:" + File.separator + chainPath;

        FabricStubConfigParser configFile = new FabricStubConfigParser(stubPath);

        FabricConnection connection = FabricConnectionFactory.build(stubPath);
        connection.start();

        // Check proxy chaincode
        if (connection.hasProxyDeployed2AllPeers()) {
            System.out.println("SUCCESS: WeCrossProxy has been deployed to all connected org");
        } else {
            System.out.println("Deploy WeCrossProxy to " + orgName + " ...");
            FabricStubFactory fabricStubFactory = new FabricStubFactory();
            Driver driver = fabricStubFactory.newDriver();
            Account user =
                    fabricStubFactory.newAccount(
                            accountName, "classpath:accounts" + File.separator + accountName);

            BlockHeaderManager blockHeaderManager =
                    new DirectBlockHeaderManager(driver, connection);

            String chaincodeName = configFile.getAdvanced().getProxyChaincode();
            String chaincodeFilesDir =
                    "classpath:" + chainPath + File.separator + chaincodeName + File.separator;
            byte[] code = TarUtils.generateTarGzInputStreamBytes(chaincodeFilesDir);
            deploy(orgName, connection, driver, user, blockHeaderManager, chaincodeName, code);
        }
    }

    public static void deploy(
            String orgName,
            FabricConnection connection,
            Driver driver,
            Account user,
            BlockHeaderManager blockHeaderManager,
            String chaincodeName,
            byte[] code)
            throws Exception {
        System.out.println("Deploy " + chaincodeName + " to " + orgName + " ...");

        String version = "1.0";
        String[] orgNames = {orgName};
        String channelName = connection.getChannel().getName();
        String language = "GO_LANG";
        String endorsementPolicy = "";

        String[] args = new String[] {channelName};

        Object[] installArgs = {chaincodeName, version, orgName, language, code};

        CompletableFuture<Exception> future1 = new CompletableFuture<>();
        driver.asyncCustomCommand(
                InstallCommand.NAME,
                null,
                installArgs,
                user,
                blockHeaderManager,
                connection,
                new Driver.CustomCommandCallback() {
                    @Override
                    public void onResponse(Exception error, Object response) {
                        future1.complete(error);
                    }
                });
        Exception error1 = future1.get(80, TimeUnit.SECONDS);
        if (error1 != null) {
            System.out.println("ERROR: asyncCustomCommand install error " + error1.getMessage());
            return;
        }

        if (!hasInstantiate(orgName, connection)) {

            // Fist time to instantiate. No need to instantiate twice
            Object[] instantiateArgs = {
                chaincodeName, version, orgNames, language, endorsementPolicy, args
            };

            CompletableFuture<Exception> future2 = new CompletableFuture<>();
            driver.asyncCustomCommand(
                    InstantiateCommand.NAME,
                    null,
                    instantiateArgs,
                    user,
                    blockHeaderManager,
                    connection,
                    new Driver.CustomCommandCallback() {
                        @Override
                        public void onResponse(Exception error, Object response) {
                            future2.complete(error);
                        }
                    });
            Exception error2 = future2.get(80, TimeUnit.SECONDS);
            if (error2 != null) {
                System.out.println(
                        "ERROR: asyncCustomCommand instantiate error " + error2.getMessage());

                return;
            }
        }

        System.out.println("SUCCESS: " + chaincodeName + " has been deployed to " + orgName);
    }

    private static boolean hasInstantiate(String orgName, FabricConnection connection) {
        Set<String> orgNames = connection.getProxyOrgNames(true);

        return orgNames.contains(orgName);
    }

    public static void main(String[] args) throws Exception {
        try {
            switch (args.length) {
                case 2:
                    handle2Args(args);
                    break;
                case 4:
                    handle4Args(args);
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
            default:
                usage();
        }
    }

    public static void handle4Args(String[] args) throws Exception {
        if (args.length != 4) {
            usage();
        }

        String cmd = args[0];
        String chainPath = args[1];
        String accountName = args[2];
        String orgName = args[3];

        switch (cmd) {
            case "deploy":
                deploy(chainPath, accountName, orgName);
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
            driver.asyncGetBlockHeader(
                    blockNumber,
                    connection,
                    new Driver.GetBlockHeaderCallback() {
                        @Override
                        public void onResponse(Exception e, byte[] blockHeader) {
                            callback.onResponse(e, blockHeader);
                        }
                    });
        }
    }
}
