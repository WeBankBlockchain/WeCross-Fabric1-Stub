package com.webank.wecross.stub.fabric;

import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.Account;
import com.webank.wecross.stub.BlockHeaderManager;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.Path;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.TransactionContext;
import com.webank.wecross.stub.TransactionException;
import com.webank.wecross.stub.TransactionRequest;
import com.webank.wecross.stub.TransactionResponse;
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstallCommand;
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstantiateCommand;
import com.webank.wecross.stub.fabric.proxy.ProxyChaincodeDeployment;
import com.webank.wecross.utils.TarUtils;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import org.junit.Assert;
import org.junit.Test;

public class ProxyChaincodeTest {
    private static final String chainPath = "chains/fabric";
    private static final Map<String, Account> org2User = new HashMap<>();
    private FabricConnection connection;
    private Driver driver;
    private BlockHeaderManager blockHeaderManager;
    private ResourceInfo resourceInfo;
    private String testChaincodeName = "testsacc-" + String.valueOf(System.currentTimeMillis());
    private String[] orgNames = {"Org1", "Org2"};
    private String[] orgAdminNames = {"fabric_admin_org1", "fabric_admin_org2"};

    public ProxyChaincodeTest() throws Exception {
        connection = FabricConnectionFactory.build("classpath:" + File.separator + chainPath);
        connection.start();

        driver = new FabricDriver();
        FabricStubFactory fabricStubFactory = new FabricStubFactory();

        blockHeaderManager =
                new ProxyChaincodeDeployment.DirectBlockHeaderManager(driver, connection);

        resourceInfo = new ResourceInfo();
        for (ResourceInfo info : connection.getResources()) {
            if (info.getName().equals("mycc")) {
                resourceInfo = info;
            }
        }

        for (int i = 0; i < orgNames.length; i++) {
            String orgName = orgNames[i];
            String adminName = orgAdminNames[i];
            Account orgAdmin =
                    fabricStubFactory.newAccount(
                            adminName, "classpath:accounts" + File.separator + adminName);
            org2User.put(orgName, orgAdmin);
        }

        deployProxyChaincode();
        deployTestChaincode();
    }

    public void forEachOrg(BiConsumer<String, Account> func) {
        for (Map.Entry<String, Account> entry : org2User.entrySet()) {
            func.accept(entry.getKey(), entry.getValue());
        }
    }

    public void deployProxyChaincode() throws Exception {
        forEachOrg(
                new BiConsumer<String, Account>() {
                    @Override
                    public void accept(String orgName, Account admin) {
                        try {
                            String chaincodeName = "WeCrossProxy"; // Just fix the default
                            String chaincodeFilesDir =
                                    "classpath:"
                                            + chainPath
                                            + File.separator
                                            + chaincodeName
                                            + File.separator;
                            byte[] code = TarUtils.generateTarGzInputStreamBytes(chaincodeFilesDir);

                            ProxyChaincodeDeployment.deploy(
                                    orgName,
                                    connection,
                                    driver,
                                    admin,
                                    blockHeaderManager,
                                    chaincodeName,
                                    code);
                        } catch (Exception e) {
                            System.out.println("Deploy proxy chaincode exception:" + e);
                            Assert.assertTrue(false);
                        }
                    }
                });
    }

    public void deployTestChaincode() throws Exception {
        String chaincodeFilesDir = "classpath:chaincode/sacc/";

        String version = "1.0";
        String language = "GO_LANG";
        String endorsementPolicy = "";
        byte[] code = TarUtils.generateTarGzInputStreamBytes(chaincodeFilesDir);
        String[] args = new String[] {"a", "10"};

        forEachOrg(
                new BiConsumer<String, Account>() {
                    @Override
                    public void accept(String orgName, Account admin) {
                        Object[] installArgs = {
                            testChaincodeName, version, orgName, language, code
                        };

                        CompletableFuture<Exception> future1 = new CompletableFuture<>();
                        driver.asyncCustomCommand(
                                InstallCommand.NAME,
                                null,
                                installArgs,
                                admin,
                                blockHeaderManager,
                                connection,
                                new Driver.CustomCommandCallback() {
                                    @Override
                                    public void onResponse(Exception error, Object response) {
                                        if (error != null) {
                                            System.out.println(
                                                    "asyncCustomCommand install error " + error);
                                        }
                                        future1.complete(error);
                                    }
                                });
                    }
                });

        Object[] instantiateArgs = {
            testChaincodeName, version, orgNames, language, endorsementPolicy, args
        };

        CompletableFuture<Exception> future2 = new CompletableFuture<>();
        driver.asyncCustomCommand(
                InstantiateCommand.NAME,
                null,
                instantiateArgs,
                org2User.get("Org1"), // choose one
                blockHeaderManager,
                connection,
                new Driver.CustomCommandCallback() {
                    @Override
                    public void onResponse(Exception error, Object response) {
                        if (error != null) {
                            System.out.println("asyncCustomCommand instantiate error " + error);
                        }
                        future2.complete(error);
                    }
                });
        Assert.assertTrue(future2.get(80, TimeUnit.SECONDS) == null);

        ((FabricConnection) connection).updateChaincodeMap();

        Set<String> names = new HashSet<>();
        for (ResourceInfo resourceInfo : connection.getResources()) {
            names.add(resourceInfo.getName());
        }
        System.out.println(names);
        Assert.assertTrue(names.contains(testChaincodeName));
    }

    @Test
    public void allTest() throws Exception {
        callProxyTest();
        sendTransactionProxyTest();
    }

    public void callProxyTest() throws Exception {
        forEachOrg(
                new BiConsumer<String, Account>() {
                    @Override
                    public void accept(String orgName, Account admin) {
                        try {
                            TransactionRequest request = new TransactionRequest();
                            request.setMethod("get");
                            request.setArgs(new String[] {"a"});

                            TransactionContext<TransactionRequest> context =
                                    new TransactionContext<>(
                                            request,
                                            admin,
                                            Path.decode("payment.fabric." + testChaincodeName),
                                            resourceInfo,
                                            blockHeaderManager);

                            CompletableFuture<TransactionResponse> future =
                                    new CompletableFuture<>();
                            driver.asyncCallByProxy(
                                    context,
                                    connection,
                                    new Driver.Callback() {
                                        @Override
                                        public void onTransactionResponse(
                                                TransactionException transactionException,
                                                TransactionResponse transactionResponse) {
                                            if (!transactionException.isSuccess()) {
                                                System.out.println(
                                                        "asyncCallByProxy exception: "
                                                                + transactionException
                                                                        .getLocalizedMessage());
                                                future.complete(null);
                                            } else {
                                                future.complete(transactionResponse);
                                            }
                                        }
                                    });

                            TransactionResponse response = future.get(30, TimeUnit.SECONDS);
                            Assert.assertTrue(response != null);

                            if (!response.getErrorCode()
                                    .equals(FabricType.TransactionResponseStatus.SUCCESS)) {
                                System.out.println(response.getErrorMessage());
                            }

                            Assert.assertTrue(
                                    response.getErrorCode()
                                            .equals(FabricType.TransactionResponseStatus.SUCCESS));

                            System.out.println(response.getResult()[0]);
                        } catch (Exception e) {
                            System.out.println("callProxyTest exception:" + e);
                            Assert.assertTrue(false);
                        }
                    }
                });
    }

    public void sendTransactionProxyTest() throws Exception {

        forEachOrg(
                new BiConsumer<String, Account>() {
                    @Override
                    public void accept(String orgName, Account admin) {
                        try {
                            String expectedResult = "20";

                            TransactionRequest request = new TransactionRequest();
                            request.setMethod("set");
                            request.setArgs(new String[] {"a", expectedResult});

                            TransactionContext<TransactionRequest> context =
                                    new TransactionContext<>(
                                            request,
                                            admin,
                                            Path.decode("payment.fabric." + testChaincodeName),
                                            resourceInfo,
                                            blockHeaderManager);

                            CompletableFuture<TransactionResponse> future =
                                    new CompletableFuture<>();
                            driver.asyncSendTransactionByProxy(
                                    context,
                                    connection,
                                    new Driver.Callback() {
                                        @Override
                                        public void onTransactionResponse(
                                                TransactionException transactionException,
                                                TransactionResponse transactionResponse) {
                                            if (!transactionException.isSuccess()) {
                                                System.out.println(
                                                        "asyncCallByProxy exception: "
                                                                + transactionException
                                                                        .getLocalizedMessage());
                                                future.complete(null);
                                            } else {
                                                future.complete(transactionResponse);
                                            }
                                        }
                                    });

                            TransactionResponse response = future.get(30, TimeUnit.SECONDS);
                            Assert.assertTrue(response != null);

                            if (!response.getErrorCode()
                                    .equals(FabricType.TransactionResponseStatus.SUCCESS)) {
                                System.out.println(response.getErrorMessage());
                            }

                            Assert.assertTrue(
                                    response.getErrorCode()
                                            .equals(FabricType.TransactionResponseStatus.SUCCESS));

                            System.out.println(response.getResult()[0]);

                            Assert.assertEquals(expectedResult, response.getResult()[0]);
                        } catch (Exception e) {
                            System.out.println("callProxyTest exception:" + e);
                            Assert.assertTrue(false);
                        }
                    }
                });
    }
}
