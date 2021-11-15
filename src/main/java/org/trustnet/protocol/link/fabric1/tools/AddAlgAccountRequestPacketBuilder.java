package org.trustnet.protocol.link.fabric1.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.wecross.account.FabricAccountFactory;
import com.webank.wecross.stub.ObjectMapperFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.hyperledger.fabric.sdk.User;
import org.trustnet.protocol.algorithm.ecdsa.secp256r1.EcdsaSecp256r1WithSHA256;
import org.trustnet.protocol.link.fabric1.TnFabricUser;

public class AddAlgAccountRequestPacketBuilder {
    private static ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();

    public static void main(String[] args) {
        try {
            if (args.length != 3) {
                help();
                System.exit(1);
            }

            Security.addProvider(new BouncyCastleProvider());

            String sender = args[0];
            String chainPath = args[1];
            String name = args[2];

            System.out.println(build(sender, chainPath, name));
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public static void help() {
        System.out.println(
                "java -cp conf/:lib/*:plugin/* "
                        + AddAlgAccountRequestPacketBuilder.class.getName()
                        + " <sender> <chain path> <account name>");
        System.out.println("e.g:");
        System.out.println(
                "java -cp conf/:lib/*:plugin/* "
                        + AddAlgAccountRequestPacketBuilder.class.getName()
                        + " 0xaaabbcc payment.fabric fabric_admin");
    }

    public static String build(String sender, String chainPath, String name) throws Exception {
        String accountPath = "classpath:accounts" + File.separator + name;

        String type = EcdsaSecp256r1WithSHA256.TYPE;
        User user = FabricAccountFactory.buildUser(name, accountPath);
        byte[] pub = readPubKeyBytesFromCert(user.getEnrollment().getCert());
        byte[] sec = getPrivateKeyBytes(user.getEnrollment().getKey());
        String cert = user.getEnrollment().getCert();
        String mspid = user.getMspId();

        AddAlgAccountRequest addAlgAccountRequest = new AddAlgAccountRequest();
        addAlgAccountRequest.setType(type);
        addAlgAccountRequest.setPubKey(pub);
        addAlgAccountRequest.setSecKey(sec);
        addAlgAccountRequest.setDefault(true);
        addAlgAccountRequest.setIdentity(sender);
        addAlgAccountRequest.setTnSign(new byte[0]);
        addAlgAccountRequest.setNonce(System.currentTimeMillis());
        addAlgAccountRequest.setProperty(
                TnFabricUser.prefix("cert", chainPath), user.getEnrollment().getCert());
        addAlgAccountRequest.setProperty(TnFabricUser.prefix("mspid", chainPath), mspid);
        addAlgAccountRequest.setProperty(TnFabricUser.prefix("name", chainPath), name);

        RestRequest<AddAlgAccountRequest> request = new RestRequest<>();
        request.setData(addAlgAccountRequest);

        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(request);
    }

    private static byte[] readPubKeyBytesFromCert(String certContent) throws Exception {
        InputStream stream = new ByteArrayInputStream(certContent.getBytes(StandardCharsets.UTF_8));

        CertificateFactory f = CertificateFactory.getInstance("X.509", new BouncyCastleProvider());
        X509Certificate certificate = (X509Certificate) f.generateCertificate(stream);

        BCECPublicKey publicKey = (BCECPublicKey) certificate.getPublicKey();
        byte[] publicKeyBytes = publicKey.getQ().getEncoded(false);
        return publicKeyBytes;
    }

    private static byte[] getPrivateKeyBytes(PrivateKey privateKey) {
        return ((BCECPrivateKey) privateKey).getD().toByteArray();
    }
}
