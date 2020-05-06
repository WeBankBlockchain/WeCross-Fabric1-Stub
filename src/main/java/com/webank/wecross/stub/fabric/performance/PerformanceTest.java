package com.webank.wecross.stub.fabric.performance;

import java.io.File;
import java.math.BigInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PerformanceTest {

    private static Logger logger = LoggerFactory.getLogger(PerformanceTest.class);

    public static void usage() {
        System.out.println("Usage:");

        System.out.println(
                " \t java -cp conf/:lib/*:plugin/* com.webank.wecross.stub.fabric.performance.PerformanceTest [chainName]  call [count] [qps]");
        System.out.println(
                " \t java -cp conf/:lib/*:plugin/* com.webank.wecross.stub.fabric.performance.PerformanceTest [chainName]  sendTransaction [count] [qps]");
        System.out.println("Example:");
        System.out.println(
                " \t java -cp conf/:lib/*:plugin/* com.webank.wecross.stub.fabric.performance.PerformanceTest chains/fabric call 10000 1000");
        System.out.println(
                " \t java -cp conf/:lib/*:plugin/* com.webank.wecross.stub.fabric.performance.PerformanceTest chains/fabric sendTransaction 10000 1000");

        exit();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            usage();
        }

        String chainName = "classpath:" + File.separator + args[0];
        String command = args[1];
        BigInteger count = new BigInteger(args[2]);
        BigInteger qps = new BigInteger(args[3]);

        System.out.println(
                "FabricPerformanceTest: command is "
                        + command
                        + ", count is "
                        + count
                        + ", qps is "
                        + qps);

        switch (command) {
            case "call":
                callTest(chainName, count, qps);
                exit();
            case "sendTransaction":
                sendTransactionTest(chainName, count, qps);
                exit();
            default:
                usage();
        }
    }

    public static void callTest(String chainName, BigInteger count, BigInteger qps) {
        try {
            PerformanceSuite suite = new PureFabricCallSuite(chainName);
            PerformanceManager performanceManager = new PerformanceManager(suite, count, qps);
            performanceManager.run();
        } catch (Exception e) {
            System.out.println("Error: " + e + " please check logs/error.log");
        }
    }

    public static void sendTransactionTest(String chainName, BigInteger count, BigInteger qps) {
        try {
            PerformanceSuite suite = new PureFabricSendTransactionSuite(chainName);
            PerformanceManager performanceManager = new PerformanceManager(suite, count, qps);
            performanceManager.run();
        } catch (Exception e) {
            System.out.println("Error: " + e + " please check logs/error.log");
        }
    }

    private static void exit() {
        System.exit(0);
    }
}
