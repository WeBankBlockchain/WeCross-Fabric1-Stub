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
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstallChaincodeRequest;
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstantiateChaincodeRequest;
import com.webank.wecross.stub.fabric.proxy.ProxyChaincodeDeployment;
import com.webank.wecross.utils.FabricUtils;
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
    private ResourceInfo testChaincodeResourceInfo;
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

        resourceInfo = new ResourceInfo();
        for (ResourceInfo info : connection.getResources()) {
            if (info.getName().equals("mycc")) {
                resourceInfo = info;
            }

            if (info.getName().equals(testChaincodeName)) {
                testChaincodeResourceInfo = info;
            }
        }
    }

    public void forEachOrg(BiConsumer<String, Account> func) {
        for (Map.Entry<String, Account> entry : org2User.entrySet()) {
            func.accept(entry.getKey(), entry.getValue());
        }
    }

    public void deployProxyChaincode() throws Exception {
        try {
            String chainPath = "chains/fabric";
            if (!ProxyChaincodeDeployment.hasInstantiate(chainPath)) {
                ProxyChaincodeDeployment.deploy(chainPath);
            }

        } catch (Exception e) {
            System.out.println("Deploy proxy chaincode exception:" + e);
            Assert.assertTrue(false);
        }
    }

    public void deployTestChaincode() throws Exception {
        String chaincodeFilesDir = "classpath:chaincode" + File.separator + "sacc" + File.separator;

        String version = "1.0";
        String language = "GO_LANG";
        String endorsementPolicy =
                FabricUtils.readPolicyYamlFileToBytesString(
                        chaincodeFilesDir + File.separator + "policy.yaml");
        byte[] code = TarUtils.generateTarGzInputStreamBytesFoGoChaincode(chaincodeFilesDir);
        String[] args = new String[] {"a", "10"};

        forEachOrg(
                new BiConsumer<String, Account>() {
                    @Override
                    public void accept(String orgName, Account admin) {
                        InstallChaincodeRequest installChaincodeRequest =
                                InstallChaincodeRequest.build()
                                        .setName(testChaincodeName)
                                        .setVersion(version)
                                        .setOrgName(orgName)
                                        .setChannelName("mychannel")
                                        .setChaincodeLanguage(language)
                                        .setCode(code);

                        TransactionContext<InstallChaincodeRequest> installRequest =
                                new TransactionContext<InstallChaincodeRequest>(
                                        installChaincodeRequest,
                                        admin,
                                        null,
                                        null,
                                        blockHeaderManager);

                        CompletableFuture<TransactionException> future1 = new CompletableFuture<>();
                        ((FabricDriver) driver)
                                .asyncInstallChaincode(
                                        installRequest,
                                        connection,
                                        new Driver.Callback() {
                                            @Override
                                            public void onTransactionResponse(
                                                    TransactionException transactionException,
                                                    TransactionResponse transactionResponse) {
                                                future1.complete(transactionException);
                                            }
                                        });
                        try {
                            TransactionException e1 = future1.get(50, TimeUnit.SECONDS);
                            if (!e1.isSuccess()) {
                                throw e1;
                            }
                        } catch (Exception e) {
                            System.out.println(e);
                        }
                    }
                });

        InstantiateChaincodeRequest instantiateChaincodeRequest =
                InstantiateChaincodeRequest.build()
                        .setName(testChaincodeName)
                        .setVersion(version)
                        .setOrgNames(orgNames)
                        .setChannelName("mychannel")
                        .setChaincodeLanguage(language)
                        .setEndorsementPolicy(endorsementPolicy)
                        // .setTransientMap()
                        .setArgs(args);
        TransactionContext<InstantiateChaincodeRequest> instantiateRequest =
                new TransactionContext<InstantiateChaincodeRequest>(
                        instantiateChaincodeRequest,
                        org2User.get("Org1"),
                        null,
                        null,
                        blockHeaderManager);

        CompletableFuture<TransactionException> future2 = new CompletableFuture<>();
        ((FabricDriver) driver)
                .asyncInstantiateChaincode(
                        instantiateRequest,
                        connection,
                        new Driver.Callback() {
                            @Override
                            public void onTransactionResponse(
                                    TransactionException transactionException,
                                    TransactionResponse transactionResponse) {
                                future2.complete(transactionException);
                            }
                        });

        Assert.assertTrue(future2.get(80, TimeUnit.SECONDS).isSuccess());

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
                                            testChaincodeResourceInfo,
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
                                            testChaincodeResourceInfo,
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

                            System.out.println(
                                    testChaincodeName + " -> " + response.getResult()[0]);

                            Assert.assertEquals(expectedResult, response.getResult()[0]);
                        } catch (Exception e) {
                            System.out.println("callProxyTest exception:" + e);
                            Assert.assertTrue(false);
                        }
                    }
                });
    }
}
