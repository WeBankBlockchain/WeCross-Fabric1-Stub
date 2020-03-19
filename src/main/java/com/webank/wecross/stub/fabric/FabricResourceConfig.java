package com.webank.wecross.stub.fabric;

import org.hyperledger.fabric.sdk.ChaincodeID;

public class FabricResourceConfig {
    private String name;
    private String type;
    private String chainCodeName;
    private String chainLanguage;
    private ChaincodeID chaincodeID;
    private long proposalWaitTime;
    private org.hyperledger.fabric.sdk.TransactionRequest.Type chainCodeType;
}
