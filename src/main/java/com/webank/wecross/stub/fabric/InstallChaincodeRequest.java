package com.webank.wecross.stub.fabric;

import static com.webank.wecross.common.FabricType.stringTochainCodeType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

public class InstallChaincodeRequest {
    // Chaincode to install:
    // name: request -> resourceInfo -> properties -> chaincodeName
    // version: always be 1.0
    // code: request -> data
    // language: request -> resourceInfo -> properties -> chaincodeType

    private String name;
    private String channelName;
    private String version;
    private byte[] code;
    private String chaincodeLanguage; // JAVA, GO_LANG, NONE
    private String orgName;

    private static ObjectMapper objectMapper = new ObjectMapper();

    InstallChaincodeRequest() {
        // use buildProposalRequest() to new this class
    }

    public static InstallChaincodeRequest build() {
        InstallChaincodeRequest defaultProposal = new InstallChaincodeRequest();
        return defaultProposal.setVersion("1.0").setChaincodeLanguage("GO_LANG");
    }

    public InstallChaincodeRequest setName(String name) {
        this.name = name;
        return this;
    }

    public InstallChaincodeRequest setChannelName(String channelName) {
        this.channelName = channelName;
        return this;
    }

    public InstallChaincodeRequest setVersion(String version) {
        this.version = version;
        return this;
    }

    public InstallChaincodeRequest setCode(byte[] code) {
        this.code = code;
        return this;
    }

    public InstallChaincodeRequest setChaincodeLanguage(String chaincodeLanguage) {
        this.chaincodeLanguage = chaincodeLanguage;
        return this;
    }

    public InstallChaincodeRequest setOrgName(String orgName) {
        this.orgName = orgName;
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

        if (getCode() == null) {
            throw new Exception("Code is null");
        }

        if (getChaincodeLanguage() == null) {
            throw new Exception("ChaincodeLanguage is null");
        }

        if (getOrgName() == null) {
            throw new Exception("OrgName is null");
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

    public byte[] getCode() {
        return code;
    }

    public String getChaincodeLanguage() {
        return chaincodeLanguage;
    }

    public String getOrgName() {
        return orgName;
    }

    @JsonIgnore
    public static InstallChaincodeRequest parseFrom(byte[] bytes)
            throws IOException, JsonParseException, JsonMappingException {
        return objectMapper.readValue(bytes, InstallChaincodeRequest.class);
    }

    @JsonIgnore
    public org.hyperledger.fabric.sdk.TransactionRequest.Type getChaincodeLanguageType()
            throws Exception {
        return stringTochainCodeType(getChaincodeLanguage());
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

        if (this.code == null) {
            throw new Exception("code not set");
        }

        if (this.chaincodeLanguage == null) {
            throw new Exception("chaincodeLanguage not set");
        }

        if (this.orgName == null) {
            throw new Exception("orgName not set");
        }
    }
}
