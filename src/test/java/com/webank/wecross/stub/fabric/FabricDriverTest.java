package com.webank.wecross.stub.fabric;

import com.webank.wecross.common.FabricType;
import com.webank.wecross.common.Utils;
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
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstallCommand;
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstantiateCommand;
import java.util.Arrays;
import java.util.HashSet;
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
    private Account admin;
    private ResourceInfo resourceInfo;

    private MockBlockHeaderManager blockHeaderManager;

    public FabricDriverTest() {
        FabricStubFactory fabricStubFactory = new FabricStubFactory();
        driver = (FabricDriver) fabricStubFactory.newDriver();
        connection = fabricStubFactory.newConnection("classpath:chains/fabric/");
        account = fabricStubFactory.newAccount("fabric_user1", "classpath:accounts/fabric_user1/");
        admin = fabricStubFactory.newAccount("fabric_admin", "classpath:accounts/fabric_admin/");
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
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        driver.asyncGetBlockHeader(
                1,
                connection,
                new Driver.GetBlockHeaderCallback() {
                    @Override
                    public void onResponse(Exception e, byte[] blockHeader) {
                        if (e != null) {
                            System.out.println("asyncGetBlockHeader exception: " + e);
                            future.complete(null);
                        } else {
                            future.complete(blockHeader);
                        }
                    }
                });

        byte[] blockBytes = future.get(10, TimeUnit.SECONDS);

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
        sendOneTransaction();
        long blockNumber = getBlockNumber(driver, connection);
        Assert.assertTrue(blockNumber > 0);
        System.out.println(blockNumber);

        sendOneTransaction();
        int waitingTimes = 0;
        while (blockNumber == getBlockNumber(driver, connection)) {
            Thread.sleep(1000);
            waitingTimes++;
            Assert.assertTrue(waitingTimes < 30);
        }
    }

    @Test
    public void verifyTransactionTest() throws Exception {
        long blockNumber = getBlockNumber(driver, connection);
        for (int i = 1; i < blockNumber; i++) {
            CompletableFuture<byte[]> future = new CompletableFuture<>();
            driver.asyncGetBlockHeader(
                    1,
                    connection,
                    new Driver.GetBlockHeaderCallback() {
                        @Override
                        public void onResponse(Exception e, byte[] blockHeader) {
                            if (e != null) {
                                System.out.println("asyncGetBlockHeader exception: " + e);
                                future.complete(null);
                            } else {
                                future.complete(blockHeader);
                            }
                        }
                    });

            byte[] blockBytes = future.get(10, TimeUnit.SECONDS);

            FabricBlock block = FabricBlock.encode(blockBytes);
            System.out.println(block.toString());

            Set<String> txList = block.parseValidTxIDListFromDataAndFilter();
            System.out.println(txList.toString());

            for (String txID : txList) {
                Assert.assertTrue(block.hasTransaction(txID));
            }
        }
    }

    private long getBlockNumber(Driver driver, Connection connection) throws Exception {
        CompletableFuture<Long> future = new CompletableFuture<>();
        driver.asyncGetBlockNumber(
                connection,
                new Driver.GetBlockNumberCallback() {
                    @Override
                    public void onResponse(Exception e, long blockNumber) {
                        if (e != null) {
                            System.out.println("getBlockNumber exception: " + e);
                            future.complete(new Long(-1));
                        } else {
                            future.complete(new Long(blockNumber));
                        }
                    }
                });
        Long blockNumber = future.get(20, TimeUnit.SECONDS);
        return blockNumber.longValue();
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

        CompletableFuture<VerifiedTransaction> future = new CompletableFuture<>();
        driver.asyncGetVerifiedTransaction(
                txHash,
                blockNumber,
                blockHeaderManager,
                connection,
                new Driver.GetVerifiedTransactionCallback() {
                    @Override
                    public void onResponse(Exception e, VerifiedTransaction verifiedTransaction) {
                        if (e != null) {
                            System.out.println("asyncGetVerifiedTransaction exception: " + e);
                            future.complete(null);
                        } else {
                            future.complete(verifiedTransaction);
                        }
                    }
                });
        VerifiedTransaction verifiedTransaction = future.get(30, TimeUnit.SECONDS);
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

    @Test
    public void deployTest() throws Exception {
        String chaincodeFilesDir = "classpath:chaincode/sacc/";
        String chaincodeName = "testchaincode-" + String.valueOf(System.currentTimeMillis());
        String version = "1.0";
        String orgName = "Org1";
        String channelName = "mychannel";
        String language = "GO_LANG";
        String[] args = new String[] {"a", "10"};

        InstallChaincodeRequest installChaincodeRequest =
                InstallChaincodeRequest.build()
                        .setName(chaincodeName)
                        .setVersion(version)
                        .setOrgName(orgName)
                        .setChannelName(channelName)
                        .setChaincodeLanguage(language)
                        .setCode(Utils.generateTarGzInputStreamBytes(chaincodeFilesDir));

        TransactionContext<InstallChaincodeRequest> installRequest =
                new TransactionContext<InstallChaincodeRequest>(
                        installChaincodeRequest, admin, null, blockHeaderManager);

        CompletableFuture<TransactionException> future1 = new CompletableFuture<>();
        driver.asyncInstallChaincode(
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

        TransactionException e1 = future1.get(50, TimeUnit.SECONDS);
        if (!e1.isSuccess()) {
            System.out.println(e1.toString());
        }
        Assert.assertTrue(e1.isSuccess());

        InstantiateChaincodeRequest instantiateChaincodeRequest =
                InstantiateChaincodeRequest.build()
                        .setName(chaincodeName)
                        .setVersion(version)
                        .setOrgName(orgName)
                        .setChannelName(channelName)
                        .setChaincodeLanguage(language)
                        // .setEndorsementPolicy()
                        // .setTransientMap()
                        .setArgs(args);
        TransactionContext<InstantiateChaincodeRequest> instantiateRequest =
                new TransactionContext<InstantiateChaincodeRequest>(
                        instantiateChaincodeRequest, admin, null, blockHeaderManager);

        CompletableFuture<TransactionException> future2 = new CompletableFuture<>();
        driver.asyncInstantiateChaincode(
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

        TransactionException e2 = future2.get(50, TimeUnit.SECONDS);
        if (!e2.isSuccess()) {
            System.out.println(e2.toString());
        }
        Assert.assertTrue(e2.isSuccess());

        ((FabricConnection) connection).updateChaincodeMap();

        Set<String> names = new HashSet<>();
        for (ResourceInfo resourceInfo : connection.getResources()) {
            names.add(resourceInfo.getName());
        }
        System.out.println(names);
        Assert.assertTrue(names.contains(chaincodeName));
    }

    @Test
    public void customCommandDeployTest() throws Exception {
        String chaincodeFilesDir = "classpath:chaincode/sacc/";
        String chaincodeName = "testchaincode-" + String.valueOf(System.currentTimeMillis());
        String version = "1.0";
        String orgName = "Org1";
        String channelName = "mychannel";
        String language = "GO_LANG";
        String endorsementPolicy = "OutOf()";
        byte[] code = Utils.generateTarGzInputStreamBytes(chaincodeFilesDir);
        String[] args = new String[] {"a", "10"};

        System.out.println(InstallCommand.DESCRIPTION);
        Object[] installArgs = {chaincodeName, version, orgName, channelName, language, code};

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
                            System.out.println("asyncCustomCommand install error " + error);
                        }
                        future1.complete(error);
                    }
                });
        Assert.assertTrue(future1.get(50, TimeUnit.SECONDS) == null);

        System.out.println(InstantiateCommand.DESCRIPTION);
        Object[] instantiateArgs = {
            chaincodeName, version, orgName, channelName, language, endorsementPolicy, args
        };

        CompletableFuture<Exception> future2 = new CompletableFuture<>();
        driver.asyncCustomCommand(
                InstantiateCommand.NAME,
                null,
                instantiateArgs,
                admin,
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
        Assert.assertTrue(future2.get(50, TimeUnit.SECONDS) == null);

        ((FabricConnection) connection).updateChaincodeMap();

        Set<String> names = new HashSet<>();
        for (ResourceInfo resourceInfo : connection.getResources()) {
            names.add(resourceInfo.getName());
        }
        System.out.println(names);
        Assert.assertTrue(names.contains(chaincodeName));
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
