package com.webank.wecross.stub.fabric;

import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.Response;
import io.netty.util.Timeout;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SendTransactionOrdererCallback {
    private Logger logger = LoggerFactory.getLogger(SendTransactionOrdererCallback.class);

    private CompletableFuture<BlockEvent.TransactionEvent> future;
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
                        .errorMessage("Invoke orderer timeout");

        onResponseInternal(response);
    }

    public CompletableFuture<BlockEvent.TransactionEvent> getFuture() {
        return future;
    }

    public void setFuture(CompletableFuture<BlockEvent.TransactionEvent> future) {
        this.future = future;
    }

    public Timeout getTimeout() {
        return timeout;
    }

    public void setTimeout(Timeout timeout) {
        this.timeout = timeout;
    }
}
