package com.webank.wecross.stub.fabric;

import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.Account;
import com.webank.wecross.stub.BlockHeaderManager;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.TransactionContext;
import com.webank.wecross.stub.TransactionException;
import com.webank.wecross.stub.TransactionRequest;
import com.webank.wecross.stub.TransactionResponse;
import com.webank.wecross.stub.fabric.proxy.ProxyChaincodeDeployment;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ProxyChaincodeTest {
    private static final String chainPath = "chains/fabric";
    private static final String accountName = "fabric_admin";
    private static final String orgName = "Org1";

    private FabricConnection connection;
    private Driver driver;
    private Account user;
    private BlockHeaderManager blockHeaderManager;
    private ResourceInfo resourceInfo;

    public ProxyChaincodeTest() throws Exception {
        connection =
                FabricConnectionFactory.build("classpath:" + File.separator + chainPath);
        connection.start();

        driver = new FabricDriver();
        FabricStubFactory fabricStubFactory = new FabricStubFactory();
        user =
                fabricStubFactory.newAccount(
                        accountName, "classpath:accounts" + File.separator + accountName);

        blockHeaderManager = new ProxyChaincodeDeployment.DirectBlockHeaderManager(driver, connection);

        resourceInfo = new ResourceInfo();
        for (ResourceInfo info : connection.getResources()) {
            if (info.getName().equals("abac")) {
                resourceInfo = info;
            }
        }

        ProxyChaincodeDeployment.deploy(orgName, connection, driver, user, blockHeaderManager);
    }

    @Test
    public void callProxyTest() throws Exception {
        TransactionRequest request = new TransactionRequest();
        request.setMethod("query");
        request.setArgs(new String[]{"a"});
        request.setPath("payment.fabric.abac");

        TransactionContext<TransactionRequest> context = new TransactionContext<>(request, user, resourceInfo, blockHeaderManager);

        CompletableFuture<TransactionResponse> future = new CompletableFuture<>();
        driver.asyncCallByProxy(context, connection, new Driver.Callback() {
            @Override
            public void onTransactionResponse(TransactionException transactionException, TransactionResponse transactionResponse) {
                if (!transactionException.isSuccess()) {
                    System.out.println("asyncCallByProxy exception: " + transactionException.getLocalizedMessage());
                    future.complete(null);
                } else {
                    future.complete(transactionResponse);
                }
            }
        });

        TransactionResponse response = future.get(30, TimeUnit.SECONDS);
        Assert.assertTrue(response != null);

        if (!response.getErrorCode().equals(FabricType.TransactionResponseStatus.SUCCESS)) {
            System.out.println(response.getErrorMessage());
        }

        Assert.assertTrue(response.getErrorCode().equals(FabricType.TransactionResponseStatus.SUCCESS));

        System.out.println(response.getResult()[0]);

    }

    @Test
    public void sendTransactionProxyTest() throws Exception {
        TransactionRequest request = new TransactionRequest();
        request.setMethod("invoke");
        request.setArgs(new String[]{"a", "b", "10"});
        request.setPath("payment.fabric.abac");

        TransactionContext<TransactionRequest> context = new TransactionContext<>(request, user, resourceInfo, blockHeaderManager);

        CompletableFuture<TransactionResponse> future = new CompletableFuture<>();
        driver.asyncSendTransactionByProxy(context, connection, new Driver.Callback() {
            @Override
            public void onTransactionResponse(TransactionException transactionException, TransactionResponse transactionResponse) {
                if (!transactionException.isSuccess()) {
                    System.out.println("asyncCallByProxy exception: " + transactionException.getLocalizedMessage());
                    future.complete(null);
                } else {
                    future.complete(transactionResponse);
                }
            }
        });

        TransactionResponse response = future.get(30, TimeUnit.SECONDS);
        Assert.assertTrue(response != null);

        if (!response.getErrorCode().equals(FabricType.TransactionResponseStatus.SUCCESS)) {
            System.out.println(response.getErrorMessage());
        }

        Assert.assertTrue(response.getErrorCode().equals(FabricType.TransactionResponseStatus.SUCCESS));

        System.out.println(response.getResult()[0]);

    }
}
