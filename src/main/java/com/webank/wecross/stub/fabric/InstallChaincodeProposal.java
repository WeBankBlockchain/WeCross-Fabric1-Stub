package com.webank.wecross.stub.fabric;

import static com.webank.wecross.common.FabricType.stringTochainCodeType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

public class InstallChaincodeProposal {
    // Chaincode to install:
    // name: request -> resourceInfo -> properties -> chaincodeName
    // version: always be 1.0
    // code: request -> data
    // language: request -> resourceInfo -> properties -> chaincodeType

    private String name;
    private String version;
    private byte[] code;
    private String chaincodeLanguage; // JAVA, GO_LANG, NONE

    private static ObjectMapper objectMapper = new ObjectMapper();

    InstallChaincodeProposal() {
        // use build() to new this class
    }

    public static InstallChaincodeProposal build() {
        InstallChaincodeProposal defaultProposal = new InstallChaincodeProposal();
        return defaultProposal.setVersion("1.0").setChaincodeLanguage("GO_LANG");
    }

    public InstallChaincodeProposal setName(String name) {
        this.name = name;
        return this;
    }

    public InstallChaincodeProposal setVersion(String version) {
        this.version = version;
        return this;
    }

    public InstallChaincodeProposal setCode(byte[] code) {
        this.code = code;
        return this;
    }

    public InstallChaincodeProposal setChaincodeLanguage(String chaincodeLanguage) {
        this.chaincodeLanguage = chaincodeLanguage;
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

        if (getCode() == null) {
            throw new Exception("Code is null");
        }

        if (getChaincodeLanguage() == null) {
            throw new Exception("ChaincodeLanguage is null");
        }

        return objectMapper.writeValueAsBytes(this);
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public byte[] getCode() {
        return code;
    }

    public String getChaincodeLanguage() {
        return chaincodeLanguage;
    }

    @JsonIgnore
    public static InstallChaincodeProposal parseFrom(byte[] bytes)
            throws IOException, JsonParseException, JsonMappingException {
        return objectMapper.readValue(bytes, InstallChaincodeProposal.class);
    }

    @JsonIgnore
    public org.hyperledger.fabric.sdk.TransactionRequest.Type getChaincodeLanguageType() {
        return stringTochainCodeType(getChaincodeLanguage());
    }
}
