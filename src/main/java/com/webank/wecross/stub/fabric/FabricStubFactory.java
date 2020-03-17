package com.webank.wecross.stub.fabric;

import com.webank.wecross.account.Account;
import com.webank.wecross.account.FabricAccountFactory;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.Stub;
import com.webank.wecross.stub.StubFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Stub("fabric1.4")
public class FabricStubFactory implements StubFactory {
    private Logger logger = LoggerFactory.getLogger(FabricStubFactory.class);

    @Override
    public Driver newDriver() {
        return new FabricDriver();
    }

    @Override
    public Connection newConnection(String path) {
        return FabricConnectionFactory.build(path);
    }

    @Override
    public Account newAccount(String name, String path) {
        return FabricAccountFactory.build(name, path);
    }
}
