package com.webank.wecross.stub.fabric.performance;

public interface PerformanceSuiteCallback {
    void onSuccess(String message);

    void onFailed(String message);
}
