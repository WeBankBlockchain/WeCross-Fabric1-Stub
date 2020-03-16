package com.webank.wecross.stub.fabric;

import com.webank.wecross.account.FabricAccount;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.Peer;

import java.util.Collection;

public class FabricChaincode {
    private Channel channel;
    private Collection<Peer> endorsor;
    private FabricAccount account;

    public FabricChaincode(Channel channel, Collection<Peer> endorsor, FabricAccount account) {
        this.channel = channel;
        this.endorsor = endorsor;
        this.account = account;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public Collection<Peer> getEndorsor() {
        return endorsor;
    }

    public void setEndorsor(Collection<Peer> endorsor) {
        this.endorsor = endorsor;
    }

    public FabricAccount getAccount() {
        return account;
    }

    public void setAccount(FabricAccount account) {
        this.account = account;
    }
}
