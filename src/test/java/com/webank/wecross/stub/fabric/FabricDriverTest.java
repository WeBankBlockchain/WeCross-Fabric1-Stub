package com.webank.wecross.stub.fabric;

import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.Account;
import com.webank.wecross.stub.BlockHeader;
import com.webank.wecross.stub.BlockHeaderManager;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.TransactionContext;
import com.webank.wecross.stub.TransactionException;
import com.webank.wecross.stub.TransactionRequest;
import com.webank.wecross.stub.TransactionResponse;
import com.webank.wecross.stub.VerifiedTransaction;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.hyperledger.fabric.protos.common.Common;
import org.junit.Assert;
import org.junit.Test;

public class FabricDriverTest {
    private FabricStubFactory fabricStubFactory;
    private FabricDriver driver;
    private Connection connection;
    private Account account;
    private ResourceInfo resourceInfo;

    private MockBlockHeaderManager blockHeaderManager;

    public FabricDriverTest() {
        FabricStubFactory fabricStubFactory = new FabricStubFactory();
        driver = (FabricDriver) fabricStubFactory.newDriver();
        connection = fabricStubFactory.newConnection("classpath:chains/fabric/");
        account = fabricStubFactory.newAccount("fabric_user1", "classpath:accounts/fabric_user1/");
        resourceInfo = new ResourceInfo();
        for (ResourceInfo info : connection.getResources()) {
            if (info.getName().equals("abac")) {
                resourceInfo = info;
            }
        }

        blockHeaderManager = new MockBlockHeaderManager(driver, connection);
    }

    @Test
    public void encodeDecodeTransactionRequestTest() {
        TransactionRequest transactionRequest = new TransactionRequest();
        transactionRequest.setMethod("invoke");
        transactionRequest.setArgs(new String[] {"a", "b", "10"});

        TransactionContext<TransactionRequest> request =
                new TransactionContext<>(transactionRequest, account, resourceInfo, null);

        byte[] bytes = driver.encodeTransactionRequest(request);
        TransactionContext<TransactionRequest> requestCmp = driver.decodeTransactionRequest(bytes);

        Assert.assertEquals(
                request.getAccount().getIdentity(), requestCmp.getAccount().getIdentity());
        Assert.assertEquals(request.getData().getMethod(), requestCmp.getData().getMethod());
        Assert.assertTrue(
                Arrays.equals(request.getData().getArgs(), requestCmp.getData().getArgs()));
    }

    @Test
    public void encodeDecodeTransactionResponseTest() {
        String[] result = new String[] {"aaaa"};
        TransactionResponse response = new TransactionResponse();
        response.setResult(result);

        byte[] bytes = driver.encodeTransactionResponse(response);
        TransactionResponse responseCmp = driver.decodeTransactionResponse(bytes);
        Assert.assertTrue(Arrays.equals(response.getResult(), responseCmp.getResult()));
    }

    @Test
    public void encodeTransactionResponseTest() {
        // 1
        String[] illegalResult1 = new String[] {"aaaa", "bbbbb", "cc"};
        TransactionResponse illegalResponse1 = new TransactionResponse();
        illegalResponse1.setResult(illegalResult1);

        byte[] bytes1 = driver.encodeTransactionResponse(illegalResponse1);
        Assert.assertTrue(bytes1 == null);

        // 2
        String[] illegalResult2 = new String[] {};
        TransactionResponse illegalResponse2 = new TransactionResponse();
        illegalResponse2.setResult(illegalResult2);

        byte[] bytes2 = driver.encodeTransactionResponse(illegalResponse2);
        Assert.assertTrue(bytes2 != null);
        Assert.assertTrue(bytes2.length == 0);
    }

    @Test
    public void callTest() throws Exception {
        TransactionRequest transactionRequest = new TransactionRequest();
        transactionRequest.setMethod("query");
        transactionRequest.setArgs(new String[] {"a"});

        TransactionContext<TransactionRequest> request =
                new TransactionContext<>(
                        transactionRequest, account, resourceInfo, blockHeaderManager);

        // Just to check no throw
        TransactionResponse response = driver.call(request, connection);

        System.out.println(response.getResult()[0]);
    }

    @Test
    public void asyncCallTest() throws Exception {
        TransactionRequest transactionRequest = new TransactionRequest();
        transactionRequest.setMethod("query");
        transactionRequest.setArgs(new String[] {"a"});

        TransactionContext<TransactionRequest> request =
                new TransactionContext<>(
                        transactionRequest, account, resourceInfo, blockHeaderManager);

        CompletableFuture<TransactionResponse> future = new CompletableFuture<>();
        CompletableFuture<TransactionException> exceptionFuture = new CompletableFuture<>();

        driver.asyncCall(
                request,
                connection,
                new Driver.Callback() {
                    @Override
                    public void onTransactionResponse(
                            TransactionException exception, TransactionResponse response) {
                        exceptionFuture.complete(exception);
                        future.complete(response);
                    }
                });

        TransactionResponse response = future.get();

        Assert.assertTrue(exceptionFuture.get().isSuccess());
        System.out.println(response.getResult()[0]);
    }

    @Test
    public void sendTransactionTest() throws Exception {
        TransactionResponse response = sendOneTransaction();

        Assert.assertEquals(
                new Integer(FabricType.TransactionResponseStatus.SUCCESS), response.getErrorCode());
        System.out.println(response.getResult()[0]);
    }

    @Test
    public void asyncSendTransactionTest() throws Exception {
        TransactionResponse response = sendOneTransactionAsync();

        Assert.assertEquals(
                new Integer(FabricType.TransactionResponseStatus.SUCCESS), response.getErrorCode());
        System.out.println(response.getResult()[0]);
    }

    @Test
    public void getBlockHeaderTest() throws Exception {
        byte[] blockBytes = driver.getBlockHeader(1, connection);

        Assert.assertTrue(blockBytes != null);
        Common.Block block = Common.Block.parseFrom(blockBytes);
        System.out.println(block.toString());

        BlockHeader blockHeader = driver.decodeBlockHeader(blockBytes);
        Assert.assertTrue(blockHeader != null);
        Assert.assertTrue(blockHeader.getNumber() != 0);
        Assert.assertTrue(blockHeader.getHash().length() != 0);
        Assert.assertTrue(blockHeader.getPrevHash().length() != 0);
        Assert.assertTrue(blockHeader.getTransactionRoot().length() != 0);
    }

    @Test
    public void getBlockNumberTest() throws Exception {
        sendTransactionTest();
        long blockNumber = driver.getBlockNumber(connection);
        Assert.assertTrue(blockNumber > 0);
        System.out.println(blockNumber);

        sendTransactionTest();
        int waitingTimes = 0;
        while (blockNumber == driver.getBlockNumber(connection)) {
            Thread.sleep(1000);
            waitingTimes++;
            Assert.assertTrue(waitingTimes < 30);
        }
    }

    @Test
    public void verifyTransactionTest() throws Exception {
        long blockNumber = driver.getBlockNumber(connection);
        for (int i = 1; i < blockNumber; i++) {
            FabricBlock block = FabricBlock.encode(driver.getBlockHeader(i, connection));
            System.out.println(block.toString());

            Set<String> txList = block.parseValidTxIDListFromDataAndFilter();
            System.out.println(txList.toString());

            for (String txID : txList) {
                Assert.assertTrue(block.hasTransaction(txID));
            }
        }
    }

    @Test
    public void getVerifiedTransactionTest() throws Exception {
        TransactionRequest transactionRequest = new TransactionRequest();
        transactionRequest.setMethod("query");
        transactionRequest.setArgs(new String[] {"a"});

        TransactionContext<TransactionRequest> request =
                new TransactionContext<>(
                        transactionRequest, account, resourceInfo, blockHeaderManager);

        TransactionResponse response = driver.sendTransaction(request, connection);
        Assert.assertEquals(
                new Integer(FabricType.TransactionResponseStatus.SUCCESS), response.getErrorCode());

        String txHash = response.getHash();
        long blockNumber = response.getBlockNumber();

        VerifiedTransaction verifiedTransaction =
                driver.getVerifiedTransaction(txHash, blockNumber, blockHeaderManager, connection);

        Assert.assertEquals(blockNumber, verifiedTransaction.getBlockNumber());
        Assert.assertEquals("mycc", verifiedTransaction.getRealAddress());
        Assert.assertEquals(response.getHash(), verifiedTransaction.getTransactionHash());
        Assert.assertEquals(
                transactionRequest.getMethod(),
                verifiedTransaction.getTransactionRequest().getMethod());
        Assert.assertTrue(
                Arrays.equals(
                        transactionRequest.getArgs(),
                        verifiedTransaction.getTransactionRequest().getArgs()));
        Assert.assertTrue(
                Arrays.equals(
                        response.getResult(),
                        verifiedTransaction.getTransactionResponse().getResult()));
    }

    private TransactionResponse sendOneTransaction() throws Exception {
        TransactionRequest transactionRequest = new TransactionRequest();
        transactionRequest.setMethod("invoke");
        transactionRequest.setArgs(new String[] {"a", "b", "10"});

        TransactionContext<TransactionRequest> request =
                new TransactionContext<>(
                        transactionRequest, account, resourceInfo, blockHeaderManager);

        TransactionResponse response = driver.sendTransaction(request, connection);

        return response;
    }

    private TransactionResponse sendOneTransactionAsync() throws Exception {
        TransactionRequest transactionRequest = new TransactionRequest();
        transactionRequest.setMethod("invoke");
        transactionRequest.setArgs(new String[] {"a", "b", "10"});

        TransactionContext<TransactionRequest> request =
                new TransactionContext<>(
                        transactionRequest, account, resourceInfo, blockHeaderManager);

        CompletableFuture<TransactionResponse> future = new CompletableFuture<>();
        CompletableFuture<TransactionException> exceptionFuture = new CompletableFuture<>();
        driver.asyncSendTransaction(
                request,
                connection,
                new Driver.Callback() {
                    @Override
                    public void onTransactionResponse(
                            TransactionException exception,
                            TransactionResponse transactionResponse) {
                        exceptionFuture.complete(exception);
                        future.complete(transactionResponse);
                    }
                });

        Assert.assertTrue(exceptionFuture.get(30, TimeUnit.SECONDS).isSuccess());
        return future.get(30, TimeUnit.SECONDS);
    }

    public static class MockBlockHeaderManager implements BlockHeaderManager {
        private Driver driver;
        private Connection connection;

        public MockBlockHeaderManager(Driver driver, Connection connection) {
            this.driver = driver;
            this.connection = connection;
        }

        @Override
        public long getBlockNumber() {
            return driver.getBlockNumber(connection);
        }

        @Override
        public byte[] getBlockHeader(long l) {
            return driver.getBlockHeader(l, connection);
        }

        @Override
        public void asyncGetBlockHeader(long blockNumber, BlockHeaderCallback callback) {
            callback.onBlockHeader(getBlockHeader(blockNumber));
        }
    }
}
