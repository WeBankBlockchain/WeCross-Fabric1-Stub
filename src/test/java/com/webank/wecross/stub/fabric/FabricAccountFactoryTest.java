package com.webank.wecross.stub.fabric;

import com.webank.wecross.account.FabricAccount;
import com.webank.wecross.account.FabricAccountFactory;
import com.webank.wecross.common.FabricType;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.junit.Assert;
import org.junit.Test;

public class FabricAccountFactoryTest {
    @Test
    public void buildTest() {
        FabricAccount fabricAccount =
                FabricAccountFactory.build("fabric1", "classpath:accounts/fabric_admin");

        Assert.assertEquals("fabric1", fabricAccount.getName());
        Assert.assertEquals(fabricAccount.getType(), FabricType.Account.FABRIC_ACCOUNT);
        System.out.println(fabricAccount.getIdentity());
    }

    @Test
    public void signTest() throws Exception {
        FabricAccount fabricAccount =
                FabricAccountFactory.build("fabric1", "classpath:accounts/fabric_admin");

        byte[] message = "test_message".getBytes(StandardCharsets.UTF_8);
        byte[] signBytes = fabricAccount.sign(message);

        System.out.println(Arrays.toString(message));
        System.out.println(Arrays.toString(signBytes));
        System.out.println(Base64.getEncoder().encodeToString(signBytes));
    }
}
