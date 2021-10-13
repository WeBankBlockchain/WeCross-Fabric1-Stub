package org.trustnet.protocol.link.fabric1.tools;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.trustnet.protocol.network.TnSignData;

public class AddAlgAccountRequest extends AlgAccountOperationRequest {
    private static ObjectMapper objectMapper = new ObjectMapper();
    private static Logger logger = LoggerFactory.getLogger(AddAlgAccountRequest.class);

    private boolean isDefault = false;
    private byte[] pubKey;
    private byte[] secKey;
    private Map<String, String> properties = new HashMap<>();

    @JsonIgnore
    @Override
    public TnSignData getSignData() {
        String propsJson = "";
        try {
            propsJson = objectMapper.writeValueAsString(properties);
        } catch (Exception e) {
            logger.error("getSignData, properties encode failed. ", e);
        }

        // args: [pubKey(Base64 encoded), secKey(Base64 encoded), isDefault, properties(Json
        // encoded)]
        List<String> args = new ArrayList<>();
        args.add(Base64.getEncoder().encodeToString(getPubKey()));
        args.add(Base64.getEncoder().encodeToString(getSecKey()));
        args.add(isDefault ? "true" : "false");
        args.add(propsJson);

        TnSignData data = super.getSignData();
        data.setMethod("AddAlgAccount");
        data.setArgs(args.toArray(new String[0]));

        return data;
    }

    public byte[] getPubKey() {
        return pubKey;
    }

    public void setPubKey(byte[] pubKey) {
        this.pubKey = pubKey;
    }

    public byte[] getSecKey() {
        return secKey;
    }

    public void setSecKey(byte[] secKey) {
        this.secKey = secKey;
    }

    @JsonIgnore
    public String getProperty(String key) {
        return properties.get(key);
    }

    public void setProperty(String key, String value) {
        this.properties.put(key, value);
    }

    @JsonGetter("isDefault")
    public boolean getDefault() {
        return isDefault;
    }

    @JsonSetter("isDefault")
    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }
}
