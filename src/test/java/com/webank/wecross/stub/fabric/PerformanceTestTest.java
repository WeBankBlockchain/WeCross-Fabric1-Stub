package com.webank.wecross.stub.fabric;

import com.webank.wecross.stub.fabric.performance.PerformanceTest;
import org.junit.Assert;
import org.junit.Test;

public class PerformanceTestTest {
    @Test
    public void callTest() {
        try {
            String[] args = new String[] {"chains/fabric", "call", "100", "10"};
            PerformanceTest.main(args);
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage(), false);
        }
    }

    @Test
    public void sendTransactionTest() {
        try {
            String[] args = new String[] {"chains/fabric", "sendTransaction", "50", "5"};
            PerformanceTest.main(args);
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage(), false);
        }
    }
}
