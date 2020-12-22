package com.webank.wecross.account;

import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.Account;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.identity.SigningIdentity;
import org.hyperledger.fabric.sdk.security.CryptoSuite;

public class FabricAccount implements Account {
    private int keyID;
    private boolean isDefault;

    private User user;
    private SigningIdentity signer;

    public FabricAccount(User user) throws Exception {
        this.setUser(user);

        // ECDSA secp256r1
        this.signer =
                ExtendedIdentityFactory.getSigningIdentity(
                        CryptoSuite.Factory.getCryptoSuite(), user);
    }

    public byte[] sign(byte[] message) throws Exception {
        return signer.sign(message);
    }

    // Only in fabric stub
    public boolean verifySign(byte[] message, byte[] sig)
            throws CryptoException, InvalidArgumentException {
        return signer.verifySignature(message, sig);
    }

    @Override
    public String getName() {
        return user.getName();
    }

    @Override
    public String getType() {
        return FabricType.Account.FABRIC_ACCOUNT;
    }

    @Override
    public String getIdentity() {
        return signer.createSerializedIdentity().getIdBytes().toStringUtf8();
    }

    @Override
    public int getKeyID() {
        return keyID;
    }

    @Override
    public boolean isDefault() {
        return isDefault;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public User getUser() {
        return this.user;
    }

    public void setKeyID(int keyID) {
        this.keyID = keyID;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }
}
