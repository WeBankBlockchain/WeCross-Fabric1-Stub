package com.webank.wecross.stub.fabric;

import com.webank.wecross.stub.ResourceInfo;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class FabricConnectionFactoryTest {
    @Test
    public void buildTest() {
        FabricConnection fabricConnection = FabricConnectionFactory.build("classpath:stubs/fabric");

        List<ResourceInfo> resourceInfoList = fabricConnection.getResources();
        Assert.assertEquals(resourceInfoList.size(), 2);
    }
}
