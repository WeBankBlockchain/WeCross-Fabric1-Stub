package com.webank.wecross.stub.fabric.hub;

import com.webank.wecross.stub.StubConstant;
import com.webank.wecross.stub.fabric.*;
import java.io.File;
import java.util.Set;

public class HubChaincodeDeployment {

    public static void usage() {
        System.out.println(getUsage("chains/fabric"));
        exit();
    }

    public static String getUsage(String chainPath) {
        String pureChainPath = chainPath.replace("classpath:/", "").replace("classpath:", "");
        return "Usage:\n"
                + "         java -cp 'conf/:lib/*:plugin/*' "
                + HubChaincodeDeployment.class.getName()
                + " deploy [chainName]\n"
                + "         java -cp 'conf/:lib/*:plugin/*' "
                + HubChaincodeDeployment.class.getName()
                + " upgrade [chainName]\n"
                + "         java -cp 'conf/:lib/*:plugin/*' "
                + HubChaincodeDeployment.class.getName()
                + " getName [chainName]\n"
                + "Example:\n"
                + "         java -cp 'conf/:lib/*:plugin/*' "
                + HubChaincodeDeployment.class.getName()
                + " deploy "
                + pureChainPath
                + "\n"
                + "         java -cp 'conf/:lib/*:plugin/*' "
                + HubChaincodeDeployment.class.getName()
                + " upgrade "
                + pureChainPath
                + "\n"
                + "         java -cp 'conf/:lib/*:plugin/*' "
                + HubChaincodeDeployment.class.getName()
                + " getName "
                + pureChainPath
                + "\n";
    }

    private static void exit() {
        System.exit(0);
    }

    public static void getName(String chainPath) {
        String stubPath = "classpath:" + File.separator + chainPath;
        FabricStubFactory fabricStubFactory = new FabricStubFactory();
        FabricConnection connection = (FabricConnection) fabricStubFactory.newConnection(stubPath);

        if (connection != null) {
            System.out.println("WeCrossHub chaincode name: " + StubConstant.HUB_NAME);
        } else {
            System.out.println("WeCrossHub has not been deployed");
        }
    }

    public static void deploy(String chainPath) throws Exception {
        String stubPath = "classpath:" + File.separator + chainPath;
        FabricConnection connection = FabricConnectionFactory.build(stubPath);
        connection.start();

        String[] args = new String[] {connection.getChannel().getName()};
        SystemChaincodeUtility.deploy(
                chainPath, SystemChaincodeUtility.Hub, StubConstant.HUB_NAME, args);
    }

    public static void upgrade(String chainPath) throws Exception {
        String stubPath = "classpath:" + File.separator + chainPath;
        FabricConnection connection = FabricConnectionFactory.build(stubPath);
        connection.start();

        String[] args = new String[] {connection.getChannel().getName()};
        SystemChaincodeUtility.upgrade(chainPath, StubConstant.HUB_NAME, args);
    }

    public static boolean hasInstantiate(String chainPath) throws Exception {
        String stubPath = "classpath:" + File.separator + chainPath;
        FabricStubConfigParser configFile = new FabricStubConfigParser(stubPath);
        FabricConnection connection = FabricConnectionFactory.build(stubPath);
        connection.start();

        Set<String> orgNames = configFile.getOrgs().keySet();
        Set<String> chainOrgNames = connection.getHubOrgNames(true);

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
            case "getName":
                getName(chainPath);
                break;
            default:
                usage();
        }
    }
}
