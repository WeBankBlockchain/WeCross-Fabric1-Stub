package com.webank.wecross.account;

import java.io.ByteArrayInputStream;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.identity.X509SigningIdentity;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class X509ExtendedSigningIdentity extends X509SigningIdentity {
    private static Logger logger = LoggerFactory.getLogger(X509ExtendedSigningIdentity.class);

    public X509ExtendedSigningIdentity(CryptoSuite cryptoSuite, User user) {
        super(cryptoSuite, user);
    }

    @Override
    public boolean verifySignature(byte[] msg, byte[] sig) throws CryptoException {
        try {
            ByteArrayInputStream bis =
                    new ByteArrayInputStream(
                            this.createSerializedIdentity().getIdBytes().toByteArray());
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) cf.generateCertificate(bis);

            Signature signer = Signature.getInstance(certificate.getSigAlgName());
            signer.initVerify(certificate);
            signer.update(msg);
            return signer.verify(sig);

        } catch (Exception e) {
            logger.error("verifySignature exception: ", e);
            return false;
        }
    }
}
