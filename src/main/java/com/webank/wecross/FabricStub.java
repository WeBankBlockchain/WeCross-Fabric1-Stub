package com.webank.wecross;

import com.webank.wecross.account.Account;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.fabric.FabricStubFactory;

public class FabricStub {
    public static void main(String args[]) {
        FabricDriverTest test = new FabricDriverTest();

        test.getBlockNumberTest();
    }

    public static class FabricDriverTest {
        private FabricStubFactory fabricStubFactory;
        private Driver driver;
        private Connection connection;
        private Account account;
        private ResourceInfo resourceInfo;

        public FabricDriverTest() {
            FabricStubFactory fabricStubFactory = new FabricStubFactory();
            driver = fabricStubFactory.newDriver();
            connection = fabricStubFactory.newConnection("classpath:stubs/fabric");
            account =
                    fabricStubFactory.newAccount("fabric_user1", "classpath:accounts/fabric_user1");
            resourceInfo = new ResourceInfo();
            for (ResourceInfo info : connection.getResources()) {
                if (info.getName().equals("HelloWeCross")) {
                    resourceInfo = info;
                }
            }
        }

        public void getBlockNumberTest() {
            long blockNumber = driver.getBlockNumber(connection);

            System.out.println(blockNumber);
        }
    }
}
