package com.webank.wecross.stub.fabric.proxy;

import com.webank.wecross.stub.StubConstant;
import com.webank.wecross.stub.fabric.*;
import java.io.File;
import java.util.Set;

public class ProxyChaincodeDeployment {

    public static void usage() {
        System.out.println(getUsage("chains/fabric"));
        exit();
    }

    public static String getUsage(String chainPath) {
        String pureChainPath = chainPath.replace("classpath:/", "").replace("classpath:", "");
        return "Usage:\n"
                + "         java -cp 'conf/:lib/*:plugin/*' "
                + ProxyChaincodeDeployment.class.getName()
                + " deploy [chainName]\n"
                + "         java -cp 'conf/:lib/*:plugin/*' "
                + ProxyChaincodeDeployment.class.getName()
                + " upgrade [chainName]\n"
                + "Example:\n"
                + "         java -cp 'conf/:lib/*:plugin/*' "
                + ProxyChaincodeDeployment.class.getName()
                + " deploy "
                + pureChainPath
                + "\n"
                + "         java -cp 'conf/:lib/*:plugin/*' "
                + ProxyChaincodeDeployment.class.getName()
                + " upgrade "
                + pureChainPath
                + "";
    }

    private static void exit() {
        System.exit(0);
    }

    public static void deploy(String chainPath) throws Exception {
        String stubPath = "classpath:" + File.separator + chainPath;
        FabricConnection connection = FabricConnectionFactory.build(stubPath);
        connection.start();

        String[] args = new String[] {connection.getChannel().getName()};
        SystemChaincodeUtility.deploy(
                chainPath, SystemChaincodeUtility.Proxy, StubConstant.PROXY_NAME, args);
    }

    public static void upgrade(String chainPath) throws Exception {
        String stubPath = "classpath:" + File.separator + chainPath;
        FabricConnection connection = FabricConnectionFactory.build(stubPath);
        connection.start();

        String[] args = new String[] {connection.getChannel().getName()};
        SystemChaincodeUtility.upgrade(chainPath, StubConstant.PROXY_NAME, args);
    }

    public static boolean hasInstantiate(String chainPath) throws Exception {
        String stubPath = "classpath:" + File.separator + chainPath;

        FabricStubConfigParser configFile = new FabricStubConfigParser(stubPath);
        FabricConnection connection = FabricConnectionFactory.build(stubPath);
        connection.start();

        Set<String> orgNames = configFile.getOrgs().keySet();
        Set<String> chainOrgNames = connection.getProxyOrgNames(true);

        orgNames.removeAll(chainOrgNames);
        return orgNames.isEmpty();
    }

    public static void main(String[] args) {
        try {
            switch (args.length) {
                case 2:
                    handle2Args(args);
                    break;
                default:
                    usage();
            }
        } catch (Exception e) {
            System.out.println(e);
        } finally {
            exit();
        }
    }

    public static void handle2Args(String[] args) throws Exception {
        if (args.length != 2) {
            usage();
        }

        String cmd = args[0];
        String chainPath = args[1];

        switch (cmd) {
            case "deploy":
                deploy(chainPath);
                break;
            case "upgrade":
                upgrade(chainPath);
                break;
            default:
                usage();
        }
    }
}
