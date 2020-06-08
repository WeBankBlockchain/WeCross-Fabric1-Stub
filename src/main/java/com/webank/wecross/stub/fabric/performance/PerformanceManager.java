package com.webank.wecross.stub.fabric.performance;

import com.google.common.util.concurrent.RateLimiter;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class PerformanceManager {
    private Logger logger = LoggerFactory.getLogger(PerformanceManager.class);
    private PerformanceSuite suite;
    private BigInteger count;
    private BigInteger qps;
    private ThreadPoolTaskExecutor threadPool;
    private RateLimiter limiter;
    private Integer area;

    public PerformanceManager(PerformanceSuite suite, String count, String qps) {
        this(suite, new BigInteger(count), new BigInteger(qps));
    }

    public PerformanceManager(PerformanceSuite suite, long count, long qps) {
        this(
                suite,
                new BigInteger(Long.toString(count, 10)),
                new BigInteger(Long.toString(qps, 10)));
    }

    public PerformanceManager(PerformanceSuite suite, BigInteger count, BigInteger qps) {
        this.suite = suite;
        this.count = count;
        this.qps = qps;

        this.threadPool = new ThreadPoolTaskExecutor();
        this.threadPool.setCorePoolSize(200);
        this.threadPool.setMaxPoolSize(500);
        this.threadPool.setQueueCapacity(count.intValue());
        this.threadPool.initialize();

        this.limiter = RateLimiter.create(qps.intValue());
        this.area = count.intValue() / 10;
        this.area = this.area == 0 ? 1 : this.area;
    }

    public void run() {
        try {
            PerformanceCollector collector = new PerformanceCollector(count.intValue());

            System.out.println("Performance Test: " + suite.getName());
            System.out.println(
                    "===================================================================");
            long startTime = System.currentTimeMillis();
            AtomicInteger sended = new AtomicInteger(0);

            for (Integer i = 0; i < count.intValue(); ++i) {
                final int index = i;
                threadPool.execute(
                        new Runnable() {
                            @Override
                            public void run() {
                                limiter.acquire();
                                PerformanceSuiteCallback callback = buildCallback(collector);
                                suite.call(callback);

                                int current = sended.incrementAndGet();

                                if (current >= area && ((current % area) == 0)) {
                                    long elapsed = System.currentTimeMillis() - startTime;
                                    double sendSpeed = current / ((double) elapsed / 1000);
                                    System.out.println(
                                            "Already sended: "
                                                    + current
                                                    + "/"
                                                    + count
                                                    + " transactions"
                                                    + ",QPS="
                                                    + sendSpeed);
                                }
                            }
                        });
            }

            // end or not
            while (!collector.isEnd()) {
                Thread.sleep(3000);
                logger.info(
                        " received: {}, total: {}",
                        collector.getReceived().intValue(),
                        collector.getTotal());
            }

            // dump summary
            collector.dumpSummary();

        } catch (Exception e) {
            logger.error("Run exception: " + e);
        }
    }

    private PerformanceSuiteCallback buildCallback(PerformanceCollector collector) {
        return new PerformanceSuiteCallback() {
            private Long startTimestamp = System.currentTimeMillis();

            @Override
            public void onSuccess(String message) {
                Long cost = System.currentTimeMillis() - this.startTimestamp;
                collector.onMessage(PerformanceCollector.Status.SUCCESS, cost);
            }

            @Override
            public void onFailed(String message) {
                System.out.println("Onfailed: " + message);
                Long cost = System.currentTimeMillis() - this.startTimestamp;
                collector.onMessage(PerformanceCollector.Status.FAILED, cost);
            }
        };
    }
}
