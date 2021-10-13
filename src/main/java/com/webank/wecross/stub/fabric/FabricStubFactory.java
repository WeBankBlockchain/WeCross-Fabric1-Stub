package com.webank.wecross.stub.fabric;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.wecross.account.FabricAccountFactory;
import com.webank.wecross.stub.Account;
import com.webank.wecross.stub.BlockManager;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.ObjectMapperFactory;
import com.webank.wecross.stub.Path;
import com.webank.wecross.stub.Stub;
import com.webank.wecross.stub.StubFactory;
import com.webank.wecross.stub.WeCrossContext;
import com.webank.wecross.stub.fabric.FabricCustomCommand.CustomCommandRequest;
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstallAndInstantiateCommand;
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstallCommand;
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstantiateCommand;
import com.webank.wecross.stub.fabric.hub.HubChaincodeDeployment;
import com.webank.wecross.stub.fabric.performance.PerformanceTest;
import com.webank.wecross.stub.fabric.performance.ProxyTest;
import com.webank.wecross.stub.fabric.proxy.ProxyChaincodeDeployment;
import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Stub("Fabric1.4")
public class FabricStubFactory implements StubFactory {
    private Logger logger = LoggerFactory.getLogger(FabricStubFactory.class);

    @Override
    public void init(WeCrossContext context) {
        context.registerCommand(InstallCommand.NAME, InstallCommand.DESCRIPTION);
        context.registerCommand(InstantiateCommand.NAME, InstantiateCommand.DESCRIPTION);
    }

    @Override
    public Driver newDriver() {
        return new FabricDriver();
    }

    @Override
    public Connection newConnection(String path) {
        try {
            FabricConnection fabricConnection = FabricConnectionFactory.build(path);
            fabricConnection.start();

            // Check proxy chaincode
            if (!fabricConnection.hasProxyDeployed2AllPeers()) {
                System.out.println(ProxyChaincodeDeployment.getUsage(path));
                throw new Exception("WeCrossProxy has not been deployed to all org");
            }
            // Check hub chaincode
            if (!fabricConnection.hasHubDeployed2AllPeers()) {
                System.out.println(HubChaincodeDeployment.getUsage(path));
                throw new Exception("WeCrossHub has not been deployed to all org");
            }

            return fabricConnection;
        } catch (Exception e) {
            logger.error("newConnection exception: " + e);
            return null;
        }
    }

    public Connection newConnection(Map<String, Object> config) {

        try {
            String helpPath = "<your-chain-dir>";
            logger.info("New connection: {} type:{}", helpPath, "Fabric1.4");
            FabricConnection fabricConnection = FabricConnectionFactory.build(config);
            fabricConnection.start();
            // For luyu protocol, no need to check proxy and hub
            return fabricConnection;
        } catch (Exception e) {
            System.out.println(e);
            logger.error(" newConnection, e: ", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public Account newAccount(Map<String, Object> properties) {
        FabricAccountFactory factory = new FabricAccountFactory();
        return factory.build(properties);
    }

    // Used by default account
    public Account newAccount(String name, String path) {
        return FabricAccountFactory.build(name, path);
    }

    @Override
    public void generateAccount(String path, String[] args) {
        try {
            // Generate config file only, copy user cert from crypto-config/

            // Write config file
            String accountTemplate =
                    "[account]\n"
                            + "    type = 'Fabric1.4'\n"
                            + "    mspid = 'Org1MSP'\n"
                            + "    keystore = 'account.key'\n"
                            + "    signcert = 'account.crt'";

            String confFilePath = path + "/account.toml";
            File confFile = new File(confFilePath);
            if (!confFile.createNewFile()) {
                logger.error("Conf file exists! {}", confFile);
                return;
            }

            FileWriter fileWriter = new FileWriter(confFile);
            try {
                fileWriter.write(accountTemplate);
            } finally {
                fileWriter.close();
            }

            String name = new File(path).getName();
            System.out.println(
                    "SUCCESS: Account \""
                            + name
                            + "\" config framework has been generated to \""
                            + path
                            + "\"\nPlease copy cert file and edit account.toml");

        } catch (Exception e) {
            logger.error("Exception: ", e);
        }
    }

    @Override
    public void generateConnection(String path, String[] args) {
        try {
            String chainName = new File(path).getName();

            String accountTemplate =
                    "[common]\n"
                            + "    name = '"
                            + chainName
                            + "'\n"
                            + "    type = 'Fabric1.4'\n"
                            + "\n"
                            + "[fabricServices]\n"
                            + "    channelName = 'mychannel'\n"
                            + "    orgUserName = 'fabric_admin'\n"
                            + "    ordererTlsCaFile = 'orderer-tlsca.crt'\n"
                            + "    ordererAddress = 'grpcs://localhost:7050'\n"
                            + "\n"
                            + "[orgs]\n"
                            + "    [orgs.Org1]\n"
                            + "        tlsCaFile = 'org1-tlsca.crt'\n"
                            + "        adminName = 'fabric_admin_org1'\n"
                            + "        endorsers = ['grpcs://localhost:7051']\n"
                            + "\n"
                            + "    [orgs.Org2]\n"
                            + "        tlsCaFile = 'org2-tlsca.crt'\n"
                            + "        adminName = 'fabric_admin_org2'\n"
                            + "        endorsers = ['grpcs://localhost:9051']\n";
            String confFilePath = path + "/stub.toml";
            File confFile = new File(confFilePath);
            if (!confFile.createNewFile()) {
                logger.error("Conf file exists! {}", confFile);
                return;
            }

            FileWriter fileWriter = new FileWriter(confFile);
            try {
                fileWriter.write(accountTemplate);
            } finally {
                fileWriter.close();
            }

            // Generate proxy and hub chaincodes
            generateProxyChaincodes(path);
            generateHubChaincodes(path);

            System.out.println(
                    "SUCCESS: Chain \""
                            + chainName
                            + "\" config framework has been generated to \""
                            + path
                            + "\"\nPlease copy cert file and edit stub.toml");
        } catch (Exception e) {
            logger.error("Exception: ", e);
        }
    }

    public void generateProxyChaincodes(String path) {
        try {
            String proxyPath =
                    File.separator + "chaincode/WeCrossProxy" + File.separator + "proxy.go";
            URL proxyDir = getClass().getResource(proxyPath);
            File dest = new File(path + proxyPath);
            FileUtils.copyURLToFile(proxyDir, dest);
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public void generateHubChaincodes(String path) {
        try {
            String hubPath = File.separator + "chaincode/WeCrossHub" + File.separator + "hub.go";
            URL hubDir = getClass().getResource(hubPath);
            File dest = new File(path + hubPath);
            FileUtils.copyURLToFile(hubDir, dest);
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public void executeCustomCommand(String accountName, String content) {
        try {
            ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();
            CustomCommandRequest request =
                    objectMapper.readValue(content, new TypeReference<CustomCommandRequest>() {});

            if (request.getCommand().equals(InstallAndInstantiateCommand.NAME)) {
                executeInstallAndInstantiateCommand(request);
            } else {
                executeNormalCommand(accountName, request);
            }

            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void executeNormalCommand(String accountName, CustomCommandRequest request)
            throws Exception {
        executeNormalCommand(accountName, request, null);
    }

    public void executeNormalCommand(
            String accountName, CustomCommandRequest request, Consumer<Connection> callback)
            throws Exception {

        Path path = Path.decode(request.getPath());
        String chainPath = "chains" + File.separator + path.getChain();
        String stubPath = "classpath:" + chainPath;

        FabricStubConfigParser configFile = new FabricStubConfigParser(stubPath, "connection.toml");

        FabricConnection connection = FabricConnectionFactory.build(configFile);
        if (connection == null) {
            throw new Exception(
                    "Could not connect to Fabric network, cat log for more info. stubPath: "
                            + stubPath);
        }
        connection.start();

        Driver driver = newDriver();

        BlockManager blockManager =
                new SystemChaincodeUtility.DirectBlockManager(driver, connection);
        Account orgAdmin =
                newAccount(accountName, "classpath:accounts" + File.separator + accountName);
        if (orgAdmin == null) {
            throw new Exception("Could not init account: " + accountName);
        }

        CompletableFuture<Exception> future = new CompletableFuture<>();
        driver.asyncCustomCommand(
                request.getCommand(),
                path,
                request.getArgs().toArray(new Object[0]),
                orgAdmin,
                blockManager,
                connection,
                new Driver.CustomCommandCallback() {
                    @Override
                    public void onResponse(Exception error, Object response) {
                        if (error != null) {
                            System.out.println(error.getMessage());
                        } else {
                            System.out.println(
                                    "Success on command: "
                                            + request.getCommand()
                                            + " path: "
                                            + request.getPath());
                        }
                        future.complete(error);
                    }
                });
        try {
            Exception e = future.get(30, TimeUnit.SECONDS);
            if (e != null) {
                throw e;
            }
            if (callback != null) {
                callback.accept(connection);
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void executeInstallAndInstantiateCommand(CustomCommandRequest request) throws Exception {
        Path path = Path.decode(request.getPath());
        String chainPath = "chains" + File.separator + path.getChain();
        String stubPath = "classpath:" + chainPath;

        FabricStubConfigParser configFile = new FabricStubConfigParser(stubPath, "connection.toml");

        List<String> orgNames = new LinkedList<>();

        // for each org to install
        for (Map.Entry<String, FabricStubConfigParser.Orgs.Org> orgEntry :
                configFile.getOrgs().entrySet()) {
            String orgName = orgEntry.getKey();
            orgNames.add(orgName);
            String accountName = orgEntry.getValue().getAdminName();
            if (accountName == null) {
                throw new RuntimeException("[orgs." + orgName + "] adminName not set");
            }

            List<Object> args =
                    InstallAndInstantiateCommand.toInstallArgs(request.getArgs(), orgName);

            CustomCommandRequest installRequest = new CustomCommandRequest();
            installRequest.setCommand(InstallCommand.NAME);
            installRequest.setPath(path.toString());
            installRequest.setArgs(args);
            executeNormalCommand(accountName, installRequest);
        }
        Thread.sleep(5000);

        // instantiate
        String adminName = configFile.getFabricServices().getOrgUserName();
        if (adminName == null) {
            throw new RuntimeException("[fabricServices] orgUserName not set");
        }

        List<Object> args =
                InstallAndInstantiateCommand.toInstantiateArgs(request.getArgs(), orgNames);
        CustomCommandRequest instantiateRequest = new CustomCommandRequest();
        instantiateRequest.setCommand(InstantiateCommand.NAME);
        instantiateRequest.setPath(path.toString());
        instantiateRequest.setArgs(args);
        executeNormalCommand(
                adminName,
                instantiateRequest,
                new Consumer<Connection>() {
                    @Override
                    public void accept(Connection connection) {
                        System.out.print("Waiting for chaincode instantiation...");
                        try {
                            FabricConnection fabricConnection = (FabricConnection) connection;
                            int tryNum = 0;
                            while (!fabricConnection.hasChaincodeDeployed2AllPeers(
                                    path.getResource())) {
                                Thread.sleep(5000);
                                System.out.print(".");
                                tryNum++;
                                if (tryNum > 60) {
                                    throw new Exception("Timeout");
                                }
                            }
                            System.out.println();
                        } catch (Exception e) {
                            System.out.println(e);
                            System.exit(1);
                        }
                    }
                });
    }

    public static void help() throws Exception {
        System.out.println(
                "This is Fabric1.4 Stub Plugin. Please copy this file to router/plugin/");
        System.out.println("To deploy WeCrossProxy:");
        System.out.println(
                "    java -cp conf/:lib/*:plugin/* com.webank.wecross.stub.fabric.proxy.ProxyChaincodeDeployment ");
        System.out.println("To deploy WeCrossHub:");
        System.out.println(
                "    java -cp conf/:lib/*:plugin/* com.webank.wecross.stub.fabric.hub.HubChaincodeDeployment ");
        System.out.println("To performance test, please run the command for more info:");
        System.out.println(
                "    Pure:    java -cp conf/:lib/*:plugin/* " + PerformanceTest.class.getName());
        System.out.println(
                "    Proxy:   java -cp conf/:lib/*:plugin/* " + ProxyTest.class.getName());
        System.out.println("To run custom command:");
        System.out.println(
                "    java -cp conf/:lib/*:plugin/* "
                        + FabricStubFactory.class.getName()
                        + " customCommand <CustomCommandRequest json> <username>");
        System.out.println(
                "    java -cp conf/:lib/*:plugin/* "
                        + FabricStubFactory.class.getName()
                        + " customCommand <CustomCommandRequest json>");
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("customCommand")) {
            FabricStubFactory factory = new FabricStubFactory();
            factory.executeCustomCommand(null, args[1]);
        } else if (args.length == 3 && args[0].equals("customCommand")) {
            FabricStubFactory factory = new FabricStubFactory();
            factory.executeCustomCommand(args[1], args[2]);
        } else {
            help();
        }
    }
}
