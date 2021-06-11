package org.luyu.protocol.link.fabric1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moandjiezana.toml.Toml;
import com.webank.wecross.stub.ObjectMapperFactory;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.Assert;
import org.junit.Test;
import org.luyu.protocol.link.Connection;
import org.luyu.protocol.link.Driver;
import org.luyu.protocol.link.PluginBuilder;
import org.luyu.protocol.network.Block;
import org.luyu.protocol.network.CallRequest;
import org.luyu.protocol.network.CallResponse;
import org.luyu.protocol.network.Receipt;
import org.luyu.protocol.network.Transaction;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public class LuyuDriverTest {
    ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();
    Connection connection;
    Driver driver;

    public LuyuDriverTest() throws Exception {
        Map<String, Object> driverConfig =
                getToml("classpath:luyu/chains/fabric/driver.toml").toMap();
        Map<String, Object> connectionConfig =
                getToml("classpath:luyu/chains/fabric/connection.toml").toMap();

        driverConfig.put("chainPath", "payment.fabric");
        driverConfig.put("chainDir", "classpath:luyu/chains/fabric");

        connectionConfig.put("chainPath", "payment.fabric");
        connectionConfig.put("chainDir", "classpath:luyu/chains/fabric");

        PluginBuilder builder = new LuyuFabric1PluginBuilder();
        connection = builder.newConnection(connectionConfig);
        driver = builder.newDriver(connection, driverConfig);
    }

    public Receipt sendOneTransaction() throws Exception {
        Transaction transaction = new Transaction();
        transaction.setPath("payment.fabric.mycc");
        transaction.setMethod("invoke");
        transaction.setArgs(new String[] {"a", "b", "1"});
        transaction.setNonce(0);
        transaction.setLuyuSign(null);

        CompletableFuture<Receipt> future = new CompletableFuture<>();
        driver.sendTransaction(
                null,
                transaction,
                new Driver.ReceiptCallback() {
                    @Override
                    public void onResponse(int status, String message, Receipt receipt) {
                        System.out.println("status: " + status);
                        System.out.println("message: " + message);
                        future.complete(receipt);
                    }
                });

        Receipt receipt = future.get();
        Assert.assertTrue(receipt != null);
        System.out.println(receipt.toString());
        return receipt;
    }

    @Test
    public void sendTransactionTest() throws Exception {
        long number1 = driver.getBlockNumber();
        sendOneTransaction();
        long number2 = driver.getBlockNumber();
        Assert.assertTrue(number1 < number2);
    }

    @Test
    public void callTest() throws Exception {
        CallRequest request = new CallRequest();
        request.setPath("payment.fabric.mycc");
        request.setMethod("query");
        request.setArgs(new String[] {"a"});

        CompletableFuture<CallResponse> future = new CompletableFuture<>();
        // Thread.sleep(5000);
        driver.call(
                null,
                request,
                new Driver.CallResponseCallback() {
                    @Override
                    public void onResponse(int status, String message, CallResponse callResponse) {
                        System.out.println("status: " + status);
                        System.out.println("message: " + message);
                        future.complete(callResponse);
                    }
                });
        CallResponse callResponse = future.get();
        Assert.assertTrue(callResponse != null);
        System.out.println(callResponse.toString());
    }

    @Test
    public void listResourceTest() throws Exception {
        CompletableFuture<org.luyu.protocol.network.Resource[]> future = new CompletableFuture<>();
        driver.listResources(
                new Driver.ResourcesCallback() {
                    @Override
                    public void onResponse(
                            int status,
                            String message,
                            org.luyu.protocol.network.Resource[] resources) {
                        System.out.println("status: " + status);
                        System.out.println("message: " + message);
                        future.complete(resources);
                    }
                });
        org.luyu.protocol.network.Resource[] resources = future.get();
        Assert.assertTrue(resources != null);
        System.out.println(Arrays.toString(resources));
    }

    @Test
    public void getBlockTest() throws Exception {
        CompletableFuture<Block> future = new CompletableFuture<>();
        driver.getBlockByNumber(
                0,
                new Driver.BlockCallback() {
                    @Override
                    public void onResponse(int status, String message, Block block) {
                        System.out.println("status: " + status);
                        System.out.println("message: " + message);
                        future.complete(block);
                    }
                });
        Block block = future.get();
        Assert.assertTrue(block != null);
        System.out.println(block.getHash());
    }

    @Test
    public void getBlockNumberTest() throws Exception {
        long number = driver.getBlockNumber();
        System.out.println(number);
        Assert.assertTrue(number > 0);
    }

    private Toml getToml(String path) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource resource = resolver.getResource(path);
        return new Toml().read(resource.getInputStream());
    }
}
