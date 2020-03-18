package com.webank.wecross.stub.fabric;

import com.webank.wecross.account.Account;
import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.TransactionContext;
import com.webank.wecross.stub.TransactionRequest;
import com.webank.wecross.stub.TransactionResponse;
import org.hyperledger.fabric.protos.common.Common;
import org.junit.Assert;
import org.junit.Test;

public class FabricStubTest {
    @Test
    public void callTest() {
        FabricStubFactory fabricStubFactory = new FabricStubFactory();

        Connection connection = fabricStubFactory.newConnection("classpath:stubs/fabric");
        ResourceInfo resourceInfo = new ResourceInfo();
        for (ResourceInfo info : connection.getResources()) {
            if (info.getName().equals("HelloWeCross")) {
                resourceInfo = info;
            }
        }

        Account account =
                fabricStubFactory.newAccount("fabric_user1", "classpath:accounts/fabric_user1");

        TransactionRequest transactionRequest = new TransactionRequest();
        transactionRequest.setMethod("query");
        transactionRequest.setArgs(new String[] {"a"});

        TransactionContext<TransactionRequest> request =
                new TransactionContext<>(transactionRequest, account, resourceInfo);

        Driver driver = new FabricDriver();

        TransactionResponse response = driver.call(request, connection);

        Assert.assertEquals(
                response.getErrorCode(), new Integer(FabricType.ResponseStatus.SUCCESS));
        System.out.println(response.getResult()[0]);
    }

    @Test
    public void sendTransactionTest() {
        FabricStubFactory fabricStubFactory = new FabricStubFactory();

        Connection connection = fabricStubFactory.newConnection("classpath:stubs/fabric");
        ResourceInfo resourceInfo = new ResourceInfo();
        for (ResourceInfo info : connection.getResources()) {
            if (info.getName().equals("HelloWeCross")) {
                resourceInfo = info;
            }
        }

        Account account =
                fabricStubFactory.newAccount("fabric_user1", "classpath:accounts/fabric_user1");

        TransactionRequest transactionRequest = new TransactionRequest();
        transactionRequest.setMethod("invoke");
        transactionRequest.setArgs(new String[] {"a", "b", "10"});

        TransactionContext<TransactionRequest> request =
                new TransactionContext<>(transactionRequest, account, resourceInfo);

        Driver driver = new FabricDriver();

        TransactionResponse response = driver.sendTransaction(request, connection);

        Assert.assertEquals(
                response.getErrorCode(), new Integer(FabricType.ResponseStatus.SUCCESS));
        System.out.println(response.getResult()[0]);
    }

    @Test
    public void getBlockHeaderTest() throws Exception {
        FabricStubFactory fabricStubFactory = new FabricStubFactory();

        Connection connection = fabricStubFactory.newConnection("classpath:stubs/fabric");

        Driver driver = new FabricDriver();

        byte[] blockBytes = driver.getBlockHeader(1, connection);

        Assert.assertTrue(blockBytes != null);

        Common.Block block = Common.Block.parseFrom(blockBytes);

        System.out.println(block.toString());
    }

    @Test
    public void getBlockNumberTest() throws Exception {
        FabricStubFactory fabricStubFactory = new FabricStubFactory();

        Connection connection = fabricStubFactory.newConnection("classpath:stubs/fabric");

        Driver driver = new FabricDriver();

        long blockNumber = driver.getBlockNumber(connection);

        Assert.assertTrue(blockNumber != 0);

        System.out.println(blockNumber);
    }
}
