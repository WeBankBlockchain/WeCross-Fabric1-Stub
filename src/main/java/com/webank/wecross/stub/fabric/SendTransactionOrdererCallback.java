package com.webank.wecross.stub.fabric;

import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.Response;
import io.netty.util.Timeout;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SendTransactionOrdererCallback {
    private Logger logger = LoggerFactory.getLogger(SendTransactionOrdererCallback.class);

    private Timeout timeout;
    private AtomicBoolean hasResponsed = new AtomicBoolean(false);

    public abstract void onResponse(Response response);

    public void onResponseInternal(Response response) {
        if (hasResponsed.get()) {
            return;
        }

        boolean responsed = hasResponsed.getAndSet(true);

        if (responsed) {
            return;
        }

        if (getTimeout() != null) {
            getTimeout().cancel();
        }

        onResponse(response);
    }

    public void onTimeout() {
        FabricConnectionResponse response =
                FabricConnectionResponse.build()
                        .errorCode(
                                FabricType.TransactionResponseStatus.FABRIC_COMMIT_CHAINCODE_FAILED)
                        .errorMessage(
                                "Invoke orderer timeout(Have you configured all endorsements policy required peers in resource?)");

        onResponseInternal(response);
    }

    public Timeout getTimeout() {
        return timeout;
    }

    public void setTimeout(Timeout timeout) {
        this.timeout = timeout;
    }
}
