package link.luyu.protocol.link.fabric1.tools;

import com.fasterxml.jackson.annotation.JsonIgnore;
import link.luyu.protocol.common.Default;
import link.luyu.protocol.network.LuyuSignData;

public class AlgAccountOperationRequest {
    private byte[] luyuSign;
    private String type;
    private long nonce;
    private String identity;

    @JsonIgnore
    public LuyuSignData getSignData() {
        LuyuSignData data = new LuyuSignData();
        data.setPath(getType()); // just use type as path in AlgAccountOperation requests
        data.setNonce(getNonce());
        data.setSender(getIdentity());
        data.setVersion(Default.PROTOCOL_VERSION);
        return data;
    }

    public byte[] getLuyuSign() {
        return luyuSign;
    }

    public void setLuyuSign(byte[] luyuSign) {
        this.luyuSign = luyuSign;
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
