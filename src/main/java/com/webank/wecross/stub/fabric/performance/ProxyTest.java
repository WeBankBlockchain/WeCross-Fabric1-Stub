package com.webank.wecross.stub.fabric.performance;

import java.io.File;
import java.math.BigInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyTest {

    private static Logger logger = LoggerFactory.getLogger(ProxyTest.class);

    public static void usage() {
        System.out.println("Usage:");

        System.out.println(
                " \t java -cp conf/:lib/*:plugin/* "
                        + ProxyTest.class.getName()
                        + " [chainName] call [count] [qps]");
        System.out.println(
                " \t java -cp conf/:lib/*:plugin/* "
                        + ProxyTest.class.getName()
                        + " [chainName] sendTransaction [count] [qps]");
        System.out.println("Example:");
        System.out.println(
                " \t java -cp conf/:lib/*:plugin/* "
                        + ProxyTest.class.getName()
                        + " chains/fabric call 10000 1000");
        System.out.println(
                " \t java -cp conf/:lib/*:plugin/* "
                        + ProxyTest.class.getName()
                        + " chains/fabric sendTransaction 10000 1000");

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
                "ProxyPerformanceTest: command is "
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
            PerformanceSuite suite = new ProxyCallSuite(chainName);
            PerformanceManager performanceManager = new PerformanceManager(suite, count, qps);
            performanceManager.run();
        } catch (Exception e) {
            System.out.println("Error: " + e + " please check logs/error.log");
            exit(1);
        }
    }

    public static void sendTransactionTest(String chainName, BigInteger count, BigInteger qps) {
        try {
            PerformanceSuite suite = new ProxySendTransactionSuite(chainName);
            PerformanceManager performanceManager = new PerformanceManager(suite, count, qps);
            performanceManager.run();
        } catch (Exception e) {
            System.out.println("Error: " + e + " please check logs/error.log");
            exit(1);
        }
    }

    private static void exit() {
        System.exit(0);
    }

    private static void exit(int sig) {
        System.exit(sig);
    }
}
