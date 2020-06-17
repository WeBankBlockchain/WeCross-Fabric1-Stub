package com.webank.wecross.stub.fabric.proxy;

import com.webank.wecross.common.Utils;
import com.webank.wecross.stub.Account;
import com.webank.wecross.stub.BlockHeaderManager;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.fabric.FabricConnection;
import com.webank.wecross.stub.fabric.FabricConnectionFactory;
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstallCommand;
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstantiateCommand;
import com.webank.wecross.stub.fabric.FabricDriver;
import com.webank.wecross.stub.fabric.FabricStubFactory;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ProxyChaincodeDeployment {
    private static final String ProxyName = ProxyChaincodeResource.NAME;

    public static void usage() {
        System.out.println("Usage:");

        System.out.println(
                " \t java -cp conf/:lib/*:plugin/* com.webank.wecross.stub.fabric.proxy.ProxyChaincodeDeployment [chainName] [accountName] [orgName]");
        System.out.println("Example:");
        System.out.println(
                " \t java -cp conf/:lib/*:plugin/* com.webank.wecross.stub.fabric.proxy.ProxyChaincodeDeployment chains/fabric fabric_admin Org1");

        exit();
    }

    private static void exit() {
        System.exit(0);
    }

    private static void exit(int sig) {
        System.exit(sig);
    }

    private static void deploy(String chainPath, String accountName, String orgName)
            throws Exception {
        FabricConnection connection =
                FabricConnectionFactory.build("classpath:" + File.separator + chainPath);
        connection.start();

        if (hasDeployed(connection)) {
            System.out.println("OK: " + ProxyName + " chaincode has already been deployed.");
            exit();
        }

        Driver driver = new FabricDriver();
        FabricStubFactory fabricStubFactory = new FabricStubFactory();
        Account user =
                fabricStubFactory.newAccount(
                        accountName, "classpath:accounts" + File.separator + accountName);

        BlockHeaderManager blockHeaderManager = new DirectBlockHeaderManager(driver, connection);

        String chaincodeFilesDir =
                "classpath:chaincode" + File.separator + ProxyName + File.separator;
        String chaincodeName = ProxyName;
        String version = "2.0";
        String org = orgName;
        String channelName = connection.getChannel().getName();
        String language = "GO_LANG";
        String endorsementPolicy = "OutOf()";
        byte[] code = Utils.generateTarGzInputStreamBytes(chaincodeFilesDir);
        String[] args = new String[] {channelName};

        Object[] installArgs = {chaincodeName, version, org, language, code};

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
        Exception error1 = future1.get(50, TimeUnit.SECONDS);
        if (error1 != null) {
            System.out.println("ERROR: asyncCustomCommand install error " + error1);
            exit();
        }

        Object[] instantiateArgs = {chaincodeName, version, org, language, endorsementPolicy, args};

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
        Exception error2 = future2.get(50, TimeUnit.SECONDS);
        if (error2 != null) {
            System.out.println("ERROR: asyncCustomCommand install error " + error2);
            exit();
        }

        if (!hasDeployed(connection)) {
            System.out.println("ERROR: Deploy finished but proxy seen to be inactive");
            exit();
        }

        System.out.println("SUCCESS: " + ProxyName + " has deployed!");
    }

    private static boolean hasDeployed(FabricConnection connection) {
        connection.updateChaincodeMap();

        Set<String> names = new HashSet<>();
        for (ResourceInfo resourceInfo : connection.getResources()) {
            names.add(resourceInfo.getName());
        }
        return names.contains(ProxyName);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            usage();
        }

        String chainPath = args[0];
        String accountName = args[1];
        String orgName = args[2];

        deploy(chainPath, accountName, orgName);
        exit();
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
