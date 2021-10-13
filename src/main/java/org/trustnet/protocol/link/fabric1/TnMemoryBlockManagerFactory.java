package org.trustnet.protocol.link.fabric1;

import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TnMemoryBlockManagerFactory {
    private ExecutorService executor = Executors.newFixedThreadPool(20);

    public TnMemoryBlockManager build(String chainPath, Driver driver, Connection connection) {
        TnMemoryBlockManager blockManager = new TnMemoryBlockManager();
        blockManager.setThreadPool(executor);
        blockManager.setDriver(driver);
        blockManager.setTimer(new Timer("blk-sync-" + chainPath));
        blockManager.setConnection(connection);
        return blockManager;
    }
}
