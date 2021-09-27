package link.luyu.protocol.link.fabric1;

import com.webank.wecross.exception.WeCrossException;
import com.webank.wecross.stub.Block;
import com.webank.wecross.stub.BlockManager;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LuyuMemoryBlockManager implements BlockManager {
    private static final Logger logger = LoggerFactory.getLogger(LuyuMemoryBlockManager.class);

    private ExecutorService threadPool;
    private Map<Long, List<GetBlockCallback>> getBlockCallbacks =
            new HashMap<Long, List<GetBlockCallback>>();
    private LinkedList<Block> blockDataCache = new LinkedList<>();
    private Driver driver;
    private Connection connection;
    private AtomicBoolean running = new AtomicBoolean(false);
    private TimerTask syncTask;
    private Timer timer;
    private ReadWriteLock lock = new ReentrantReadWriteLock();
    private long getBlockNumberDelay = 1000;
    private int maxCacheSize = 20;
    private AtomicLong latestBlockNumber = new AtomicLong(-1L);
    private long lastBlockNumberAcquired = 0;

    private static class Status {
        public static final int Starting = 0;
        public static final int OK = 1;
        public static final int Failure = 2;
    }

    private AtomicInteger fetchBlockNumberStatus = new AtomicInteger(Status.Starting);

    public long getLastBlockNumberAcquired() {
        return lastBlockNumberAcquired;
    }

    public void setLastBlockNumberAcquired(long lastBlockNumberAcquired) {
        this.lastBlockNumberAcquired = lastBlockNumberAcquired;
    }

    public boolean hasBlock(long number) {
        if (blockDataCache.size() == 0) {
            return false;
        }

        long max = blockDataCache.peekFirst().getBlockHeader().getNumber();
        long min = max - blockDataCache.size() + 1;

        return min <= number && number <= max;
    }

    public void onGetBlockNumber(Exception e, long blockNumber) {

        if (Objects.nonNull(e)) {
            logger.warn("onGetBlockNumber failed, e: ", e);
            fetchBlockNumberStatus.set(Status.Failure);
            waitAndSyncBlock(getGetBlockNumberDelay());
            return;
        }

        // reset latestBlockNumber field
        latestBlockNumber.set(blockNumber);
        fetchBlockNumberStatus.set(Status.OK);

        if (logger.isTraceEnabled()) {
            logger.trace(
                    "onGetBlockNumber, blockNumber: {}, lastBlockNumberAcquired: {}",
                    blockNumber,
                    lastBlockNumberAcquired);
        }

        long current = lastBlockNumberAcquired;
        /*
        lock.readLock().lock();
        try {
            if (!blockDataCache.isEmpty()) {
                current = blockDataCache.peekLast().getBlockHeader().getNumber();
            }
        } finally {
            lock.readLock().unlock();
        }*/

        if (current < blockNumber) {
            long blockNumberToGet = 0;
            if (current == 0) {
                blockNumberToGet = blockNumber;
            } else {
                blockNumberToGet = current + 1;
            }

            long finalBlockNumberToGet = blockNumberToGet;
            driver.asyncGetBlock(
                    blockNumberToGet,
                    true,
                    connection,
                    (error, data) -> {
                        onSyncBlock(error, data, finalBlockNumberToGet, blockNumber);
                    });

        } else {
            waitAndSyncBlock(getGetBlockNumberDelay());
        }
    }

    public void onSyncBlock(Exception e, Block block, long currentBlockNumber, long target) {

        long blockNumber = currentBlockNumber;
        if (Objects.isNull(e)) {
            blockNumber = block.getBlockHeader().getNumber();
        }

        lock.writeLock().lock();
        try {
            List<GetBlockCallback> callbacks = getBlockCallbacks.get(blockNumber);
            if (callbacks != null) {
                for (GetBlockCallback callback : callbacks) {
                    threadPool.execute(
                            new Runnable() {
                                @Override
                                public void run() {
                                    callback.onResponse(e, block);
                                }
                            });
                }
                getBlockCallbacks.remove(blockNumber);
            }

            if (Objects.isNull(e)) {
                blockDataCache.add(block);
                if (blockDataCache.size() > maxCacheSize) {
                    blockDataCache.pop();
                }
            }

        } finally {
            lock.writeLock().unlock();
        }

        lastBlockNumberAcquired = blockNumber;

        if (Objects.isNull(e)) {

            if (logger.isTraceEnabled()) {
                logger.trace(
                        "onSyncBlock, blockNumber: {}, blockHash: {}",
                        block.getBlockHeader().getNumber(),
                        block.getBlockHeader().getHash());
            }

            if (block.getBlockHeader().getNumber() < target) {
                driver.asyncGetBlock(
                        block.getBlockHeader().getNumber() + 1,
                        false,
                        connection,
                        (error, blockData) -> {
                            onSyncBlock(
                                    error,
                                    blockData,
                                    block.getBlockHeader().getNumber() + 1,
                                    target);
                        });
            } else {
                waitAndSyncBlock(0);
            }
        } else {
            logger.warn("onSyncBlock failed, currentBlockNumber: {}, e: ", currentBlockNumber, e);
            waitAndSyncBlock(getGetBlockNumberDelay());
        }
    }

    private void waitAndSyncBlock(long delay) {
        if (running.get()) {
            synchronized (running) {
                if (syncTask != null) {
                    syncTask.cancel();
                }

                syncTask =
                        new TimerTask() {
                            @Override
                            public void run() {
                                driver.asyncGetBlockNumber(
                                        connection,
                                        new Driver.GetBlockNumberCallback() {
                                            @Override
                                            public void onResponse(Exception e, long blockNumber) {
                                                onGetBlockNumber(e, blockNumber);
                                            }
                                        });
                            }
                        };

                timer.schedule(syncTask, delay);
            }
        }
    }

    @Override
    public void start() {
        Connection connection = LuyuMemoryBlockManager.this.connection;
        if (connection != null && driver != null && running.compareAndSet(false, true)) {
            logger.info("MemoryBlockHeaderManager started");

            driver.asyncGetBlockNumber(
                    connection,
                    new Driver.GetBlockNumberCallback() {
                        @Override
                        public void onResponse(Exception e, long blockNumber) {
                            onGetBlockNumber(e, blockNumber);
                            if (Objects.isNull(e)) {
                                logger.info(
                                        "MemoryBlockManager initialize successfully, blockNumber: {}",
                                        blockNumber);
                            } else {
                                logger.error("MemoryBlockManager initialize failed, e: ", e);
                            }
                        }
                    });
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("MemoryBlockHeaderManager stopped");

            for (List<GetBlockCallback> callbacks : getBlockCallbacks.values()) {
                for (GetBlockCallback callback : callbacks) {
                    threadPool.execute(
                            new Runnable() {
                                @Override
                                public void run() {
                                    callback.onResponse(
                                            new WeCrossException(-1, "Operation canceled"), null);
                                }
                            });
                }
            }

            blockDataCache.clear();
            getBlockCallbacks.clear();
            timer.cancel();
        }
    }

    @Override
    public void asyncGetBlockNumber(GetBlockNumberCallback callback) {

        long blockNumber = 0;
        WeCrossException e = null;
        if (fetchBlockNumberStatus.get() == Status.OK) {
            blockNumber = latestBlockNumber.get();
        } else {
            e =
                    new WeCrossException(
                            WeCrossException.ErrorCode.GET_BLOCK_NUMBER_ERROR,
                            "get blockNumber failed");
        }

        long finalBlockNumber = blockNumber;
        Exception finalE = e;
        threadPool.execute(
                () -> {
                    callback.onResponse(finalE, finalBlockNumber);
                });
    }

    @Override
    public void asyncGetBlock(long blockNumber, GetBlockCallback callback) {
        lock.writeLock().lock();

        try {
            if (blockDataCache.isEmpty()
                    || blockNumber < blockDataCache.peekFirst().getBlockHeader().getNumber()) {
                driver.asyncGetBlock(
                        blockNumber,
                        false,
                        connection,
                        (error, data) -> {
                            callback.onResponse(error, data);
                        });
            } else if (blockNumber > blockDataCache.peekLast().getBlockHeader().getNumber()) {
                if (!getBlockCallbacks.containsKey(blockNumber)) {
                    getBlockCallbacks.put(blockNumber, new LinkedList<GetBlockCallback>());
                }

                getBlockCallbacks.get(blockNumber).add(callback);
                waitAndSyncBlock(0); // query blockNumber right now

            } else {
                for (Block block : blockDataCache) {
                    if (block.getBlockHeader().getNumber() == blockNumber) {
                        threadPool.execute(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.onResponse(null, block);
                                    }
                                });

                        break;
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    public Timer getTimer() {
        return timer;
    }

    public void setTimer(Timer timer) {
        this.timer = timer;
    }

    public long getGetBlockNumberDelay() {
        return getBlockNumberDelay;
    }

    public void setGetBlockNumberDelay(long getBlockNumberDelay) {
        this.getBlockNumberDelay = getBlockNumberDelay;
    }

    public int getMaxCacheSize() {
        return maxCacheSize;
    }

    public void setMaxCacheSize(int maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
    }
}
