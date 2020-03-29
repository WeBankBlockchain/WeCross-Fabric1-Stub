package com.webank.wecross.common;

public class FabricType {
    public static final class Account {
        public static final String FABRIC_ACCOUNT = "FABRIC_ACCOUNT";
    }

    public static final class ConnectionMessage {
        // Connection send message type
        public static final int FABRIC_CALL = 2001;
        public static final int FABRIC_SENDTRANSACTION_ENDORSER = 2002;
        public static final int FABRIC_SENDTRANSACTION_ORDERER = 2003;
        public static final int FABRIC_GET_BLOCK_NUMBER = 2004;
        public static final int FABRIC_GET_BLOCK_HEADER = 2005;
        public static final int FABRIC_GET_TRANSACTION = 2006;
    }

    public static class Resource {
        public static final String RESOURCE_TYPE_FABRIC_CONTRACT = "FABRIC_CONTRACT";
    }

    public static class ResourceInfoProperty {
        // ResourceInfo properties name
        public static final String CHANNEL_NAME = "CHANNEL_NAME";
        public static final String CHAINCODE_NAME = "CHAINCODE_NAME";
        public static final String CHAINCODE_TYPE = "CHAINCODE_TYPE";
        public static final String PROPOSAL_WAIT_TIME = "PROPOSAL_WAIT_TIME";
    }

    public static class TransactionResponseStatus {
        // Chaincode response errorcode
        public static final int SUCCESS = 0;
        public static final int FABRIC_INVOKE_CHAINCODE_FAILED = 3001;
        public static final int FABRIC_COMMIT_CHAINCODE_FAILED = 3002;
        public static final int FABRIC_TX_ONCHAIN_VERIFY_FAIED = 3003;

        public static final int INTERNAL_ERROR = 3101;
        public static final int ILLEGAL_REQUEST_TYPE = 3102;
        public static final int RESOURCE_NOT_FOUND = 3103;
    }
}
