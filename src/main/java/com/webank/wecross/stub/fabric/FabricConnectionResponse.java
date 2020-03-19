package com.webank.wecross.stub.fabric;

import com.webank.wecross.stub.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FabricConnectionResponse extends Response {
    private static Logger logger = LoggerFactory.getLogger(FabricConnectionResponse.class);

    public static FabricConnectionResponse build() {
        return new FabricConnectionResponse();
    }

    public FabricConnectionResponse errorCode(int errorCode) {
        super.setErrorCode(errorCode);
        return this;
    }

    public FabricConnectionResponse errorMessage(String errorMessage) {
        super.setErrorMessage(errorMessage);
        return this;
    }

    public FabricConnectionResponse data(byte[] data) {
        super.setData(data);
        return this;
    }
}
