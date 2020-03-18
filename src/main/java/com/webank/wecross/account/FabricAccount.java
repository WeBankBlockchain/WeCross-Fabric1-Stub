package com.webank.wecross.account;

import com.webank.wecross.common.FabricType;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric.sdk.identity.IdentityFactory;
import org.hyperledger.fabric.sdk.identity.SigningIdentity;
import org.hyperledger.fabric.sdk.security.CryptoSuite;

public class FabricAccount implements Account {

    public static final String CRYPTO_SUITE_FABRIC_BC_SECP256R1 = "FABRIC_BC_SECP256R1";

    public static final String PROPOSAL_TYPE_PEER_PAYLODAD = "QUERY_PEER_PAYLODAD";
    public static final String PROPOSAL_TYPE_ENDORSER_PAYLODAD = "ENDORSER_PAYLODAD";
    public static final String PROPOSAL_TYPE_ORDERER_PAYLOAD = "ORDERER_PAYLOAD"; // for fabric

    private User user;
    private SigningIdentity signer;

    public FabricAccount(User user) throws Exception {
        this.setUser(user);

        // ECDSA secp256r1
        this.signer =
                IdentityFactory.getSigningIdentity(CryptoSuite.Factory.getCryptoSuite(), user);
    }

    public byte[] sign(byte[] message) throws Exception {
        return signer.sign(message);
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
        return signer.createSerializedIdentity().toByteString().toStringUtf8();
    }

    public void setUser(User user) {
        this.user = user;
    }

    public User getUser() {
        return this.user;
    }
}
