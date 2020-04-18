package com.webank.wecross.stub.fabric;

import org.junit.Assert;
import org.junit.Test;

public class FabricStubConfigParserTest {
    @Test
    public void loadTest() throws Exception {
        FabricStubConfigParser parser = new FabricStubConfigParser("classpath:chains/fabric/");
        Assert.assertTrue(parser != null);
        Assert.assertTrue(parser.getCommon() != null);
        Assert.assertTrue(parser.getFabricServices() != null);
        Assert.assertTrue(parser.getPeers() != null);
        Assert.assertTrue(parser.getResources() != null);
    }
}
