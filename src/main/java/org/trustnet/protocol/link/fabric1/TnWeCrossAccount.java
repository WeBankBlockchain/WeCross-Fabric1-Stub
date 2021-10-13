package org.trustnet.protocol.link.fabric1;

import com.webank.wecross.account.FabricAccount;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric.sdk.identity.SigningIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.trustnet.protocol.network.Account;

public class TnWeCrossAccount extends FabricAccount {
    private static Logger logger = LoggerFactory.getLogger(TnWeCrossAccount.class);

    public TnWeCrossAccount(User user, SigningIdentity signer) throws Exception {
        super(user, signer);
    }

    public static TnWeCrossAccount build(String chainPath, Account luyuAccount) throws Exception {
        luyuAccount.getPubKey(); // test account manager is avaliable

        User user = new TnFabricUser(luyuAccount, chainPath);
        SigningIdentity signer = new TnX509SigningIdentity(luyuAccount, user);
        return new TnWeCrossAccount(user, signer);
    }

    @Override
    public int getKeyID() {
        return -1; // not used at all
    }

    @Override
    public boolean isDefault() {
        return true; // always default
    }
}
