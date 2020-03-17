package com.webank.wecross.stub.fabric;

import com.webank.wecross.account.FabricAccount;
import com.webank.wecross.account.FabricAccountFactory;
import com.webank.wecross.common.FabricType;
import org.junit.Assert;
import org.junit.Test;

public class FabricAccountFactoryTest {
    @Test
    public void buildTest() {
        FabricAccount fabricAccount =
                FabricAccountFactory.build("fabric1", "classpath:accounts/fabric1");

        Assert.assertEquals(fabricAccount.getName(), "fabric1");
        Assert.assertEquals(fabricAccount.getType(), FabricType.FABRIC_ACCOUNT);
        System.out.println(fabricAccount.getIdentity());
    }
}
