package com.webank.wecross.stub.fabric;

import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Request;
import com.webank.wecross.stub.Response;
import java.util.List;
import java.util.Map;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;

public class FabricConnection implements Connection {
    private HFClient hfClient;
    private Channel channel;
    private Map<String, FabricChaincode> chaincodeMap;

    public FabricConnection(
            HFClient hfClient, Channel channel, Map<String, FabricChaincode> chaincodeMap) {
        this.hfClient = hfClient;
        this.channel = channel;
        this.chaincodeMap = chaincodeMap;
    }

    @Override
    public Response send(Request request) {
        switch (request.getType()) {
            case FabricConnectionRequestType.FABRIC_CALL:
                return handleCall(request);

            case FabricConnectionRequestType.FABRIC_SENDTRANSACTION:
                return handleSendTransaction(request);

            case FabricConnectionRequestType.FABRIC_GET_BLOCK_NUMBER:
                return handleGetBlockNumber(request);

            case FabricConnectionRequestType.FABRIC_GET_BLOCK_HEADER:
                return handleGetBlockHeader(request);

            default:
                Response response = new Response();
                response.setErrorCode(-1);
                response.setErrorMessage("Unsupported request type: " + request.getType());
                return response;
        }
    }

    @Override
    public List<String> getResources() {
        return null;
    }

    private Response handleCall(Request request) {
        return null;
    }

    private Response handleSendTransaction(Request request) {
        return null;
    }

    private Response handleGetBlockNumber(Request request) {
        return null;
    }

    private Response handleGetBlockHeader(Request request) {
        return null;
    }
}
