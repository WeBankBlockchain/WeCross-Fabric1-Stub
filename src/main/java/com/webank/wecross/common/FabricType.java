package com.webank.wecross.common;

public class FabricType {
    public static final String FABRIC_ACCOUNT = "FABRIC_ACCOUNT";

    // Connection send message type
    public static final int FABRIC_CALL = 2001;
    public static final int FABRIC_SENDTRANSACTION_ENDORSER = 2002;
    public static final int FABRIC_SENDTRANSACTION_ORDERER = 2003;
    public static final int FABRIC_GET_BLOCK_NUMBER = 2004;
    public static final int FABRIC_GET_BLOCK_HEADER = 2005;

    // Resource type
    public static final String RESOURCE_TYPE_FABRIC_CONTRACT = "FABRIC_CONTRACT";

    // ResourceInfo properties name
    public static final String CHANNEL_NAME = "CHANNEL_NAME";
    public static final String CHAINCODE_NAME = "CHAINCODE_NAME";
    public static final String CHAINCODE_TYPE = "CHAINCODE_TYPE";
    public static final String PROPOSAL_WAIT_TIME = "PROPOSAL_WAIT_TIME";
}
