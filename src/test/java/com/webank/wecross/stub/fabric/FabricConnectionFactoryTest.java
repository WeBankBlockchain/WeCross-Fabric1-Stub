package com.webank.wecross.stub.fabric;

import com.webank.wecross.stub.ResourceInfo;
import java.util.List;
import org.hyperledger.fabric.sdk.Channel;
import org.junit.Assert;
import org.junit.Test;

public class FabricConnectionFactoryTest {
    @Test
    public void buildTest() {
        FabricConnection fabricConnection = FabricConnectionFactory.build("classpath:stubs/fabric");

        List<ResourceInfo> resourceInfoList = fabricConnection.getResources();
        Assert.assertEquals(resourceInfoList.size(), 2);
    }

    @Test
    public void resourcePropertiesTest() throws Exception {
        FabricConnection fabricConnection = FabricConnectionFactory.build("classpath:stubs/fabric");
        List<ResourceInfo> resourceInfoList = fabricConnection.getResources();

        Channel channel = fabricConnection.getChannel();
        for (ResourceInfo info : resourceInfoList) {
            ResourceInfoProperty property = ResourceInfoProperty.parseFrom(info.getProperties());
            Assert.assertEquals(channel, property.getChannel());
        }
    }
}
