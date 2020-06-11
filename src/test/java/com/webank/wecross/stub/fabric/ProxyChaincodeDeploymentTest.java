package com.webank.wecross.stub.fabric;

import com.webank.wecross.stub.fabric.proxy.ProxyChaincodeDeployment;
import org.junit.Assert;
import org.junit.Test;

public class ProxyChaincodeDeploymentTest {
    @Test
    public void mainTest() throws Exception {
        String[] args = {"chains/fabric", "fabric_admin", "Org1"};
        ProxyChaincodeDeployment.main(args);
        Assert.assertTrue(true);
    }
}
