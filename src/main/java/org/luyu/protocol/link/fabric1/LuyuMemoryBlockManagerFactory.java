package org.luyu.protocol.link.fabric1;

import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LuyuMemoryBlockManagerFactory {
    private ExecutorService executor = Executors.newFixedThreadPool(20);

    public LuyuMemoryBlockManager build(String chainPath, Driver driver, Connection connection) {
        LuyuMemoryBlockManager blockManager = new LuyuMemoryBlockManager();
        blockManager.setThreadPool(executor);
        blockManager.setDriver(driver);
        blockManager.setTimer(new Timer("blk-sync-" + chainPath));
        blockManager.setConnection(connection);
        return blockManager;
    }
}
