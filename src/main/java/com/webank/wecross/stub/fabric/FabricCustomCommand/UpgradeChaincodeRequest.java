package com.webank.wecross.stub.fabric.FabricCustomCommand;

import static com.webank.wecross.common.FabricType.stringTochainCodeType;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.wecross.utils.FabricUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.hyperledger.fabric.sdk.ChaincodeEndorsementPolicy;

public class UpgradeChaincodeRequest {
    // Chaincode to upgrade
    // name: request -> resourceInfo -> properties -> chaincodeName
    // version: always be 1.0
    // language: request -> resourceInfo -> properties -> chaincodeType
    // args: request -> data
    // policy: OutOf()
    // transientMap: new HashMap(empty map)

    private String name;
    private String channelName;
    private String version;
    private String chaincodeLanguage; // JAVA, GO_LANG, NONE
    private String endorsementPolicy;
    private Map<String, byte[]> transientMap;
    private String[] args;
    private String[] orgNames;

    private static ObjectMapper objectMapper = new ObjectMapper();

    UpgradeChaincodeRequest() {
        // Use buildProposalRequest() to new this class
    }

    public static UpgradeChaincodeRequest build() {
        Map<String, byte[]> tm = new HashMap<>();
        tm.put("HyperLedgerFabric", "UpgradeProposalRequest:JavaSDK".getBytes(UTF_8));
        tm.put("method", "UpgradeProposalRequest".getBytes(UTF_8));

        UpgradeChaincodeRequest defaultProposal = new UpgradeChaincodeRequest();
        return defaultProposal
                .setVersion("1.0")
                .setChaincodeLanguage("GO_LANG")
                .setEndorsementPolicy("") // given null will default to OR(every members)
                .setTransientMap(tm);
    }

    public UpgradeChaincodeRequest setName(String name) {
        this.name = name;
        return this;
    }

    public UpgradeChaincodeRequest setChannelName(String channelName) {
        this.channelName = channelName;
        return this;
    }

    public UpgradeChaincodeRequest setVersion(String version) {
        this.version = version;
        return this;
    }

    public UpgradeChaincodeRequest setChaincodeLanguage(String chaincodeLanguage) {
        this.chaincodeLanguage = chaincodeLanguage;
        return this;
    }

    public UpgradeChaincodeRequest setEndorsementPolicy(String endorsementPolicy) {
        this.endorsementPolicy = endorsementPolicy;
        return this;
    }

    public UpgradeChaincodeRequest setTransientMap(Map<String, byte[]> transientMap) {
        this.transientMap = transientMap;
        return this;
    }

    public UpgradeChaincodeRequest setArgs(String[] args) {
        this.args = args;
        return this;
    }

    public UpgradeChaincodeRequest setOrgNames(String[] orgNames) {
        this.orgNames = orgNames;
        return this;
    }

    @JsonIgnore
    public byte[] toBytes() throws Exception {
        if (getName() == null) {
            throw new Exception("Name is null");
        }

        if (getChannelName() == null) {
            throw new Exception("ChannelName is null");
        }

        if (getVersion() == null) {
            throw new Exception("Version is null");
        }

        if (getChaincodeLanguage() == null) {
            throw new Exception("ChaincodeLanguage is null");
        }

        if (getEndorsementPolicy() == null) {
            throw new Exception("EndorsementPolicy is null");
        }

        if (getTransientMap() == null) {
            throw new Exception("TransientMap is null");
        }

        if (getArgs() == null) {
            throw new Exception("Args is null");
        }

        if (getOrgNames() == null) {
            throw new Exception("OrgNames is null");
        }

        return objectMapper.writeValueAsBytes(this);
    }

    public String getName() {
        return name;
    }

    public String getChannelName() {
        return channelName;
    }

    public String getVersion() {
        return version;
    }

    public String getChaincodeLanguage() {
        return chaincodeLanguage;
    }

    public String getEndorsementPolicy() {
        return endorsementPolicy;
    }

    public Map<String, byte[]> getTransientMap() {
        return transientMap;
    }

    public String[] getArgs() {
        return args;
    }

    public String[] getOrgNames() {
        return orgNames;
    }

    public ArrayList<String> getArgss() {
        ArrayList<String> arrayList = new ArrayList<>(args.length);
        for (String arg : args) {
            arrayList.add(arg);
        }

        return arrayList;
    }

    @JsonIgnore
    public static UpgradeChaincodeRequest parseFrom(byte[] bytes)
            throws IOException, JsonParseException, JsonMappingException {
        return (UpgradeChaincodeRequest)
                objectMapper.readValue(bytes, UpgradeChaincodeRequest.class);
    }

    @JsonIgnore
    public org.hyperledger.fabric.sdk.TransactionRequest.Type getChaincodeLanguageType()
            throws Exception {
        return stringTochainCodeType(getChaincodeLanguage());
    }

    @JsonIgnore
    public ChaincodeEndorsementPolicy getEndorsementPolicyType() throws Exception {
        return FabricUtils.parsePolicyBytesString(getEndorsementPolicy());
    }

    public void check() throws Exception {
        if (this.name == null) {
            throw new Exception("name not set");
        }

        if (this.channelName == null) {
            throw new Exception("channelName not set");
        }

        if (this.version == null) {
            throw new Exception("version not set");
        }

        if (this.chaincodeLanguage == null) {
            throw new Exception("chaincodeLanguage not set");
        }

        if (this.endorsementPolicy == null) {
            throw new Exception("endorsementPolicy not set");
        }

        if (this.transientMap == null) {
            throw new Exception("transientMap not set");
        }

        if (this.args == null) {
            throw new Exception("args not set");
        }

        if (this.orgNames == null) {
            throw new Exception("orgNames not set");
        }
    }
}
