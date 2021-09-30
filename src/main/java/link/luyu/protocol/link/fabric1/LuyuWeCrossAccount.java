package link.luyu.protocol.link.fabric1;

import com.webank.wecross.account.FabricAccount;
import link.luyu.protocol.network.Account;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric.sdk.identity.SigningIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LuyuWeCrossAccount extends FabricAccount {
    private static Logger logger = LoggerFactory.getLogger(LuyuWeCrossAccount.class);

    public LuyuWeCrossAccount(User user, SigningIdentity signer) throws Exception {
        super(user, signer);
    }

    public static LuyuWeCrossAccount build(String chainPath, Account luyuAccount) throws Exception {
        luyuAccount.getPubKey(); // test account manager is avaliable

        User user = new LuyuFabricUser(luyuAccount, chainPath);
        SigningIdentity signer = new LuyuX509SigningIdentity(luyuAccount, user);
        return new LuyuWeCrossAccount(user, signer);
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
