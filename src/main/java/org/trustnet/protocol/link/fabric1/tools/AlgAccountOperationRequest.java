package org.trustnet.protocol.link.fabric1.tools;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.trustnet.protocol.common.Default;
import org.trustnet.protocol.network.TnSignData;

public class AlgAccountOperationRequest {
    private byte[] tnSign;
    private String type;
    private long nonce;
    private String identity;

    @JsonIgnore
    public TnSignData getSignData() {
        TnSignData data = new TnSignData();
        data.setPath(getType()); // just use type as path in AlgAccountOperation requests
        data.setNonce(getNonce());
        data.setSender(getIdentity());
        data.setVersion(Default.PROTOCOL_VERSION);
        return data;
    }

    public byte[] getTnSign() {
        return tnSign;
    }

    public void setTnSign(byte[] tnSign) {
        this.tnSign = tnSign;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getNonce() {
        return nonce;
    }

    public void setNonce(long nonce) {
        this.nonce = nonce;
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }
}
