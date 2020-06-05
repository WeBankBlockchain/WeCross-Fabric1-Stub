package com.webank.wecross.stub.fabric;

import static com.webank.wecross.common.FabricType.stringTochainCodeType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import static java.nio.charset.StandardCharsets.UTF_8;

public class InstantiateChaincodeProposal {
    // Chaincode to instantiate
    // name: request -> resourceInfo -> properties -> chaincodeName
    // version: always be 1.0
    // language: request -> resourceInfo -> properties -> chaincodeType
    // args: request -> data
    // policy: OutOf()
    // transientMap: new HashMap(empty map)

    private String name;
    private String version;
    private String chaincodeLanguage; // JAVA, GO_LANG, NONE
    private String endorsementPolicy;
    private Map<String, byte[]> transientMap;
    private String[] args;

    private static ObjectMapper objectMapper = new ObjectMapper();

    InstantiateChaincodeProposal() {
        // Use build() to new this class
    }

    public static InstantiateChaincodeProposal build() {
        Map<String, byte[]> tm = new HashMap<>();
        tm.put("HyperLedgerFabric", "InstantiateProposalRequest:JavaSDK".getBytes(UTF_8));
        tm.put("method", "InstantiateProposalRequest".getBytes(UTF_8));

        InstantiateChaincodeProposal defaultProposal = new InstantiateChaincodeProposal();
        return defaultProposal
                .setVersion("1.0")
                .setChaincodeLanguage("GO_LANG")
                .setEndorsementPolicy("OutOf()")
                .setTransientMap(tm);
    }

    public InstantiateChaincodeProposal setName(String name) {
        this.name = name;
        return this;
    }

    public InstantiateChaincodeProposal setVersion(String version) {
        this.version = version;
        return this;
    }

    public InstantiateChaincodeProposal setChaincodeLanguage(String chaincodeLanguage) {
        this.chaincodeLanguage = chaincodeLanguage;
        return this;
    }

    public InstantiateChaincodeProposal setEndorsementPolicy(String endorsementPolicy) {
        this.endorsementPolicy = endorsementPolicy;
        return this;
    }

    public InstantiateChaincodeProposal setTransientMap(Map<String, byte[]> transientMap) {
        this.transientMap = transientMap;
        return this;
    }

    public InstantiateChaincodeProposal setArgs(String[] args) {
        this.args = args;
        return this;
    }

    @JsonIgnore
    public byte[] toBytes() throws Exception {
        if (getName() == null) {
            throw new Exception("Name is null");
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

        return objectMapper.writeValueAsBytes(this);
    }

    public String getName() {
        return name;
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

    @JsonIgnore
    public static InstantiateChaincodeProposal parseFrom(byte[] bytes)
            throws IOException, JsonParseException, JsonMappingException {
        return (InstantiateChaincodeProposal)
                objectMapper.readValue(bytes, InstantiateChaincodeProposal.class);
    }

    @JsonIgnore
    public org.hyperledger.fabric.sdk.TransactionRequest.Type getChaincodeLanguageType() {
        return stringTochainCodeType(getChaincodeLanguage());
    }
}
