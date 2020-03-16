package com.webank.wecross.stub.fabric;

import com.webank.wecross.stub.Request;

public class FabricConnectionRequestType extends Request {
    public static final int FABRIC_CALL = 2001;
    public static final int FABRIC_SENDTRANSACTION = 2002;
    public static final int FABRIC_GET_BLOCK_NUMBER = 2003;
    public static final int FABRIC_GET_BLOCK_HEADER = 2004;
}
