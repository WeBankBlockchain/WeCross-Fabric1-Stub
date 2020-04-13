package com.webank.wecross.stub.fabric;

import com.webank.wecross.account.FabricAccountFactory;
import com.webank.wecross.stub.Account;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.Stub;
import com.webank.wecross.stub.StubFactory;
import java.io.File;
import java.io.FileWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Stub("Fabric1.4")
public class FabricStubFactory implements StubFactory {
    private Logger logger = LoggerFactory.getLogger(FabricStubFactory.class);

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
            // Write private key pem file
            Security.addProvider(new BouncyCastleProvider());
            KeyPairGenerator keyPairGenerator =
                    KeyPairGenerator.getInstance("EC", Security.getProvider("BC"));

            keyPairGenerator.initialize(256);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            PrivateKey ecPrivateKey = keyPair.getPrivate();

            String keyFile = path + "/account.key";
            File file = new File(keyFile);

            if (!file.createNewFile()) {
                logger.error("Key file exists! {}", keyFile);
                return;
            }

            PemWriter pemWriter = new PemWriter(new FileWriter(file));
            try {
                pemWriter.writeObject(new PemObject("PRIVATE KEY", ecPrivateKey.getEncoded()));
            } finally {
                pemWriter.close();
            }

            // Write cert pem file
            Instant now = Instant.now();
            Date notBefore = Date.from(now);
            Date notAfter = Date.from(now.plus(Duration.ofDays(3650)));

            X500Name x500Name = new X500Name("CN=");

            X509v3CertificateBuilder certificateBuilder =
                    new JcaX509v3CertificateBuilder(
                            x500Name,
                            BigInteger.valueOf(now.toEpochMilli()),
                            notBefore,
                            notAfter,
                            x500Name,
                            keyPair.getPublic());

            ContentSigner contentSigner =
                    new JcaContentSignerBuilder("SHA256WITHECDSA").build(keyPair.getPrivate());

            X509Certificate cert =
                    new JcaX509CertificateConverter()
                            .setProvider(Security.getProvider("BC"))
                            .getCertificate(certificateBuilder.build(contentSigner));

            String certFile = path + "/account.crt";
            file = new File(certFile);

            pemWriter = new PemWriter(new FileWriter(file));
            try {
                pemWriter.writeObject(new PemObject("CERTIFICATE", cert.getEncoded()));
            } finally {
                pemWriter.close();
            }

            // Write config file
            String accountTemplate =
                    "[account]\n"
                            + "    type = 'Fabric1.4'\n"
                            + "    mspid = ''\n"
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
            String accountTemplate =
                    "[common]\n"
                            + "    stub = 'fabric'\n"
                            + "    type = 'Fabric1.4'\n"
                            + "\n"
                            + "[fabricServices]\n"
                            + "    channelName = 'mychannel'\n"
                            + "    orgName = 'Org1'\n"
                            + "    mspId = 'Org1MSP'\n"
                            + "    orgUserName = 'fabric_admin'\n"
                            + "    orgUserAccountPath = 'classpath:accounts/fabric_admin'\n"
                            + "    ordererTlsCaFile = 'classpath:/stubs/fabric/orderer-tlsca.crt'\n"
                            + "    ordererAddress = 'grpcs://localhost:7050'\n"
                            + "\n"
                            + "[peers]\n"
                            + "    [peers.org1]\n"
                            + "        peerTlsCaFile = 'classpath:/stubs/fabric/org1-tlsca.crt'\n"
                            + "        peerAddress = 'grpcs://localhost:7051'\n"
                            + "    [peers.org2]\n"
                            + "         peerTlsCaFile = 'classpath:/stubs/fabric/org2-tlsca.crt'\n"
                            + "         peerAddress = 'grpcs://localhost:9051'\n"
                            + "\n"
                            + "# resources is a list\n"
                            + "[[resources]]\n"
                            + "    # name cannot be repeated\n"
                            + "    name = 'HelloWeCross'\n"
                            + "    type = 'FABRIC_CONTRACT'\n"
                            + "    chainCodeName = 'mycc'\n"
                            + "    chainLanguage = \"go\"\n"
                            + "    peers=['org1','org2']\n"
                            + "[[resources]]\n"
                            + "    name = 'HelloWorld'\n"
                            + "    type = 'FABRIC_CONTRACT'\n"
                            + "    chainCodeName = 'mygg'\n"
                            + "    chainLanguage = \"go\"\n"
                            + "    peers=['org1','org2']";
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
}
