package com.webank.wecross.account;

import com.webank.wecross.utils.FabricUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

public class FabricAccountFactory {
    static Logger logger = LoggerFactory.getLogger(FabricAccountFactory.class);

    public static FabricAccount build(String name, String accountPath) throws Exception {
        User user = buildUser(name, accountPath);
        FabricAccount account = new FabricAccount(user);
        return account;
    }

    public static User buildUser(String name, String accountPath) throws Exception {
        String accountConfigFile = accountPath + File.separator + "account.toml";
        Map<String, String> accountConfig =
                (Map<String, String>) FabricUtils.readTomlMap(accountConfigFile).get("account");

        String mspid = accountConfig.get("mspid");
        Enrollment enrollment = buildEnrollment(accountPath);
        return new User() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public Set<String> getRoles() {
                return new HashSet<String>();
            }

            @Override
            public String getAccount() {
                return "";
            }

            @Override
            public String getAffiliation() {
                return "";
            }

            @Override
            public Enrollment getEnrollment() {
                return enrollment;
            }

            @Override
            public String getMspId() {
                return mspid;
            }
        };
    }

    public static Enrollment buildEnrollment(String accountPath) throws Exception {
        String accountConfigFile = accountPath + File.separator + "account.toml";
        Map<String, String> accountConfig =
                (Map<String, String>) FabricUtils.readTomlMap(accountConfigFile).get("account");

        String keystoreFile = accountPath + File.separator + accountConfig.get("keystore");
        if (keystoreFile == null) {
            String errorMessage =
                    "\"keystore\" in [account] item not found, please check " + accountConfigFile;
            throw new Exception(errorMessage);
        }

        String signcertFile = accountPath + File.separator + accountConfig.get("signcert");
        if (signcertFile == null) {
            String errorMessage =
                    "\"certFile\" in [account] item not found, please check " + accountConfigFile;
            throw new Exception(errorMessage);
        }

        PrivateKey privateKey = loadPemPrivateKey(keystoreFile);
        String certContent = loadPemCert(signcertFile);

        return new Enrollment() {
            @Override
            public PrivateKey getKey() {
                return privateKey;
            }

            @Override
            public String getCert() {
                return certContent;
            }
        };
    }

    public static PrivateKey loadPemPrivateKey(String keyPath) throws Exception {
        PEMManager pem = new PEMManager();
        pem.setPemFile(keyPath);
        pem.load();
        return pem.getPrivateKey();
    }

    public static String loadPemCert(String certPath) throws Exception {
        try {
            PathMatchingResourcePatternResolver resolver =
                    new PathMatchingResourcePatternResolver();
            Path path = Paths.get(resolver.getResource(certPath).getURI());
            return new String(Files.readAllBytes(path));
        } catch (IOException e) {
            logger.error("getKey failed path:{} errmsg:{}", certPath, e);
            return "";
        }
    }

    public static class PEMManager {
        private PemObject pem;
        private String pemFile;

        public PEMManager() {
            Security.setProperty("crypto.policy", "unlimited");
            Security.addProvider(new BouncyCastleProvider());
        }

        public void load()
                throws KeyStoreException, NoSuchAlgorithmException, CertificateException,
                        IOException, NoSuchProviderException, InvalidKeySpecException {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource pemResources = resolver.getResource(pemFile);

            load(pemResources.getInputStream());
        }

        public void load(InputStream in)
                throws KeyStoreException, NoSuchAlgorithmException, CertificateException,
                        IOException, InvalidKeySpecException, NoSuchProviderException {
            PemReader pemReader = new PemReader(new InputStreamReader(in));

            pem = pemReader.readPemObject();
            if (pem == null) {
                throw new IOException("The file does not represent a pem account.");
            }
            pemReader.close();
        }

        public PrivateKey getPrivateKey()
                throws InvalidKeySpecException, NoSuchAlgorithmException, NoSuchProviderException {
            PKCS8EncodedKeySpec encodedKeySpec = new PKCS8EncodedKeySpec(pem.getContent());
            KeyFactory keyFacotry =
                    KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);

            return keyFacotry.generatePrivate(encodedKeySpec);
        }

        public void setPemFile(String pemFile) {
            this.pemFile = pemFile;
        }
    }
}
