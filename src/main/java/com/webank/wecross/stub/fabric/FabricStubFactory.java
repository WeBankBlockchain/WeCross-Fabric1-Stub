package com.webank.wecross.stub.fabric;

import com.webank.wecross.account.FabricAccountFactory;
import com.webank.wecross.stub.Account;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.Stub;
import com.webank.wecross.stub.StubFactory;
import com.webank.wecross.stub.WeCrossContext;
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstallCommand;
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstantiateCommand;
import java.io.File;
import java.io.FileWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Stub("Fabric1.4")
public class FabricStubFactory implements StubFactory {
    private Logger logger = LoggerFactory.getLogger(FabricStubFactory.class);

    @Override
    public void init(WeCrossContext context) {
        context.registerCommand(InstallCommand.NAME, InstallCommand.DESCRIPTION);
        context.registerCommand(InstantiateCommand.NAME, InstantiateCommand.DESCRIPTION);
    }

    @Override
    public Driver newDriver() {
        return new FabricDriver();
    }

    @Override
    public Connection newConnection(String path) {
        try {
            FabricConnection fabricConnection = FabricConnectionFactory.build(path);
            fabricConnection.start();
            return fabricConnection;
        } catch (Exception e) {
            logger.error("newConnection exception: " + e);
            return null;
        }
    }

    @Override
    public Account newAccount(String name, String path) {
        return FabricAccountFactory.build(name, path);
    }

    @Override
    public void generateAccount(String path, String[] args) {
        try {
            // Generate config file only, copy user cert from crypto-config/

            // Write config file
            String accountTemplate =
                    "[account]\n"
                            + "    type = 'Fabric1.4'\n"
                            + "    mspid = 'Org1MSP'\n"
                            + "    keystore = 'account.key'\n"
                            + "    signcert = 'account.crt'";

            String confFilePath = path + "/account.toml";
            File confFile = new File(confFilePath);
            if (!confFile.createNewFile()) {
                logger.error("Conf file exists! {}", confFile);
                return;
            }

            FileWriter fileWriter = new FileWriter(confFile);
            try {
                fileWriter.write(accountTemplate);
            } finally {
                fileWriter.close();
            }

        } catch (Exception e) {
            logger.error("Exception: ", e);
        }
    }

    @Override
    public void generateConnection(String path, String[] args) {
        try {
            String chainName = new File(path).getName();

            String accountTemplate =
                    "[common]\n"
                            + "    name = '"
                            + chainName
                            + "'\n"
                            + "    type = 'Fabric1.4'\n"
                            + "\n"
                            + "[fabricServices]\n"
                            + "    channelName = 'mychannel'\n"
                            + "    orgName = 'Org1'\n"
                            + "    mspId = 'Org1MSP'\n"
                            + "    orgUserName = 'fabric_admin'\n"
                            + "    orgUserAccountPath = 'classpath:accounts/fabric_admin'\n"
                            + "    ordererTlsCaFile = 'orderer-tlsca.crt'\n"
                            + "    ordererAddress = 'grpcs://localhost:7050'\n"
                            + "\n"
                            + "[peers]\n"
                            + "    [peers.org1]\n"
                            + "        orgName = 'Org1'\n"
                            + "        peerTlsCaFile = 'org1-tlsca.crt'\n"
                            + "        peerAddress = 'grpcs://localhost:7051'\n"
                            + "    [peers.org2]\n"
                            + "        orgName = 'Org2'\n"
                            + "        peerTlsCaFile = 'org2-tlsca.crt'\n"
                            + "        peerAddress = 'grpcs://localhost:9051'\n"
                            + "\n"
                            + "# resources is a list\n"
                            + "[[resources]]\n"
                            + "    # name cannot be repeated\n"
                            + "    type = 'FABRIC_CONTRACT'\n"
                            + "    chainCodeName = 'mycc'\n"
                            + "    peers=['org1','org2']\n";
            String confFilePath = path + "/stub.toml";
            File confFile = new File(confFilePath);
            if (!confFile.createNewFile()) {
                logger.error("Conf file exists! {}", confFile);
                return;
            }

            FileWriter fileWriter = new FileWriter(confFile);
            try {
                fileWriter.write(accountTemplate);
            } finally {
                fileWriter.close();
            }
        } catch (Exception e) {
            logger.error("Exception: ", e);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println(
                "This is Fabric1.4 Stub Plugin. Please copy this file to router/plugin/");
        System.out.println("To deploy WeCrossProxy:");
        System.out.println(
                "    java -cp conf/:lib/*:plugin/* com.webank.wecross.stub.fabric.performance.PerformanceTest ");
        System.out.println("To pure chain performance test, please run the command for more info:");
        System.out.println(
                "    java -cp conf/:lib/*:plugin/* com.webank.wecross.stub.fabric.performance.PerformanceTest ");
    }
}
