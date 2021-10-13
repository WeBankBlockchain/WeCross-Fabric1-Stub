package org.trustnet.protocol.link.fabric1;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.identity.SigningIdentity;
import org.hyperledger.fabric.sdk.identity.X509Identity;
import org.trustnet.protocol.network.Account;

public class TnX509SigningIdentity extends X509Identity implements SigningIdentity {
    private Account luyuAccount;

    public TnX509SigningIdentity(Account luyuAccount, User user) {
        super(user);
        this.luyuAccount = luyuAccount;
    }

    @Override
    public byte[] sign(byte[] msg) throws CryptoException, InvalidArgumentException {
        class Data {
            public int status;
            public String message;
            public byte[] signBytes;
        }
        CompletableFuture<Data> future = new CompletableFuture<>();

        luyuAccount.sign(
                msg,
                new Account.SignCallback() {
                    @Override
                    public void onResponse(int status, String message, byte[] signBytes) {
                        Data data = new Data();
                        data.status = status;
                        data.message = message;
                        data.signBytes = signBytes;
                        future.complete(data);
                    }
                });
        try {
            Data data = future.get(30, TimeUnit.SECONDS);
            if (data.status != 0) {
                throw new Exception(data.message);
            }
            return data.signBytes;
        } catch (Exception e) {
            throw new CryptoException(e.getMessage());
        }
    }

    @Override
    public boolean verifySignature(byte[] msg, byte[] sig)
            throws CryptoException, InvalidArgumentException {
        class Data {
            public int status;
            public String message;
            boolean verifyResult;
        }
        CompletableFuture<Data> future = new CompletableFuture<>();

        luyuAccount.verify(
                sig,
                msg,
                new Account.VerifyCallback() {
                    @Override
                    public void onResponse(int status, String message, boolean verifyResult) {
                        Data data = new Data();
                        data.status = status;
                        data.message = message;
                        data.verifyResult = verifyResult;
                        future.complete(data);
                    }
                });
        try {
            Data data = future.get(30, TimeUnit.SECONDS);
            if (data.status != 0) {
                throw new Exception(data.message);
            }
            return data.verifyResult;
        } catch (Exception e) {
            throw new CryptoException(e.getMessage());
        }
    }
}
