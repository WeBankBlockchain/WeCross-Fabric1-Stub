package com.webank.wecross.common;

public class FabricType {
    public static final String STUB_NAME = "Fabric1.4";
    public static final String FABRIC_VERIFIER = "VERIFIER";

    public static final class Account {
        public static final String FABRIC_ACCOUNT = STUB_NAME;
    }

    public static final class ConnectionMessage {
        // Connection send message type
        public static final int FABRIC_CALL = 2001;
        public static final int FABRIC_SENDTRANSACTION_ENDORSER = 2002;
        public static final int FABRIC_SENDTRANSACTION_ORDERER = 2003;
        public static final int FABRIC_GET_BLOCK_NUMBER = 2004;
        public static final int FABRIC_GET_BLOCK = 2005;
        public static final int FABRIC_GET_TRANSACTION = 2006;
        public static final int FABRIC_SENDTRANSACTION_ORG_ENDORSER = 2007;
    }

    public static class Resource {
        public static final String RESOURCE_TYPE_FABRIC_CONTRACT = "FABRIC_CONTRACT";
    }

    public static class ResourceInfoProperty {
        // ResourceInfo properties name
        public static final String CHANNEL_NAME = "CHANNEL_NAME";
        public static final String CHAINCODE_NAME = "CHAINCODE_NAME";
        public static final String CHAINCODE_VERSION = "CHAINCODE_VERSION";
        public static final String CHAINCODE_TYPE = "CHAINCODE_TYPE";
        public static final String PROPOSAL_WAIT_TIME = "PROPOSAL_WAIT_TIME";
        public static final String ORG_NAMES = "ORG_NAMES";
    }

    public static class TransactionResponseStatus {
        // Chaincode response errorcode
        public static final int SUCCESS = 0;
        public static final int FABRIC_EXECUTE_CHAINCODE_FAILED = 3000;
        public static final int FABRIC_INVOKE_CHAINCODE_FAILED = 3001;
        public static final int FABRIC_COMMIT_CHAINCODE_FAILED = 3002;
        public static final int FABRIC_TX_ONCHAIN_VERIFY_FAIED = 3003;

        public static final int INTERNAL_ERROR = 3101;
        public static final int ILLEGAL_REQUEST_TYPE = 3102;
        public static final int RESOURCE_NOT_FOUND = 3103;
        public static final int REQUEST_ENCODE_EXCEPTION = 3104;
        public static final int REQUEST_DECODE_EXCEPTION = 3105;

        public static final int GET_PROPERTIES_FAILED = 3201;
    }

    public static org.hyperledger.fabric.sdk.TransactionRequest.Type stringTochainCodeType(
            String type) throws Exception {
        switch (type) {
            case "JAVA":
                return org.hyperledger.fabric.sdk.TransactionRequest.Type.JAVA;
            case "GO_LANG":
                return org.hyperledger.fabric.sdk.TransactionRequest.Type.GO_LANG;
            case "NODE":
                return org.hyperledger.fabric.sdk.TransactionRequest.Type.NODE;
            default:
                throw new Exception("Unsupported chaincode language: " + type);
        }
    }

    public static String chainCodeTypeToString(
            org.hyperledger.fabric.sdk.TransactionRequest.Type type) {
        switch (type) {
            case JAVA:
                return "JAVA";
            case GO_LANG:
                return "GO_LANG";
            case NODE:
                return "NODE";
            default:
                return null;
        }
    }

    public static final String ORG_NAME_DEF = "orgName_w";
}
