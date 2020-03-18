package com.webank.wecross.stub.fabric;

import com.webank.wecross.stub.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChaincodeResponse extends Response {
    private static Logger logger = LoggerFactory.getLogger(ChaincodeResponse.class);

    public static ChaincodeResponse build() {
        return new ChaincodeResponse();
    }

    public ChaincodeResponse errorCode(int errorCode) {
        super.setErrorCode(errorCode);
        return this;
    }

    public ChaincodeResponse errorMessage(String errorMessage) {
        super.setErrorMessage(errorMessage);
        return this;
    }

    public ChaincodeResponse data(byte[] data) {
        super.setData(data);
        return this;
    }
}
