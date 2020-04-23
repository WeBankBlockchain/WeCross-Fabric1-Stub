package com.webank.wecross.stub.fabric.performance;

public interface PerformanceSuite {
    String getName();

    void call(PerformanceSuiteCallback callback);
}
