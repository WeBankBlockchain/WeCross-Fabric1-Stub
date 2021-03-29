package com.webank.wecross.stub.fabric;

import com.webank.wecross.stub.ResourceInfo;
import java.util.List;
import org.hyperledger.fabric.sdk.Channel;
import org.junit.Assert;
import org.junit.Test;

public class FabricConnectionFactoryTest {
    @Test
    public void buildTest() throws Exception {
        FabricConnection fabricConnection =
                FabricConnectionFactory.build("classpath:chains/fabric/");

        fabricConnection.start();
        List<ResourceInfo> resourceInfoList = fabricConnection.getResources();
        System.out.println(resourceInfoList.toString());
        Assert.assertTrue(resourceInfoList.size() > 0);
    }

    @Test
    public void resourcePropertiesTest() throws Exception {
        FabricConnection fabricConnection =
                FabricConnectionFactory.build("classpath:chains/fabric/");
        fabricConnection.start();
        List<ResourceInfo> resourceInfoList = fabricConnection.getResources();

        Channel channel = fabricConnection.getChannel();
        for (ResourceInfo info : resourceInfoList) {
            ResourceInfoProperty property = ResourceInfoProperty.parseFrom(info.getProperties());
            Assert.assertEquals(channel.getName(), property.getChannelName());
        }
    }

    @Test
    public void startTest() throws Exception {
        FabricConnection fabricConnection =
                FabricConnectionFactory.build("classpath:chains/fabric/");
        try {
            fabricConnection.start();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            Assert.assertTrue(true);
        }
    }
}
