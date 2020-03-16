package com.webank.wecross.stub.fabric;

import com.webank.wecross.account.Account;
import com.webank.wecross.account.FabricAccountFactory;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.Stub;
import com.webank.wecross.stub.StubFactory;

@Stub("fabric1.4")
public class FabricStubFactory implements StubFactory {
    @Override
    public Driver newDriver() {
        return null;
    }

    @Override
    public Connection newConnection(String path) {
        return null;
    }

    @Override
    public Account newAccount(String name, String path) {
        try {
            Account account = FabricAccountFactory.build(name, path);
            return account;
        } catch (Exception e) {
            return null;
        }
    }
}
