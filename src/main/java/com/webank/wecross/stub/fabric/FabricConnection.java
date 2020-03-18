package com.webank.wecross.stub.fabric;

import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Request;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.Response;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;

public class FabricConnection implements Connection {
    private HFClient hfClient;
    private Channel channel;
    private Map<String, ChaincodeConnection> chaincodeMap;

    public FabricConnection(
            HFClient hfClient, Channel channel, Map<String, ChaincodeConnection> chaincodeMap) {
        this.hfClient = hfClient;
        this.channel = channel;
        this.chaincodeMap = chaincodeMap;
    }

    public void start() throws Exception {
        channel.initialize();
    }

    @Override
    public Response send(Request request) {
        switch (request.getType()) {
            case FabricType.ConnectionMessage.FABRIC_CALL:
                return handleCall(request);

            case FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ENDORSER:
                return handleSendTransactioneEndorser(request);

            case FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ORDERER:
                return handleSendTransactioneOrderer(request);

            case FabricType.ConnectionMessage.FABRIC_GET_BLOCK_NUMBER:
                return handleGetBlockNumber(request);

            case FabricType.ConnectionMessage.FABRIC_GET_BLOCK_HEADER:
                return handleGetBlockHeader(request);

            default:
                Response response = new Response();
                response.setErrorCode(-1);
                response.setErrorMessage("Unsupported request type: " + request.getType());
                return response;
        }
    }

    @Override
    public List<ResourceInfo> getResources() {
        List<ResourceInfo> resourceInfoList = new LinkedList<>();
        for (ChaincodeConnection chaincodeConnection : chaincodeMap.values()) {
            resourceInfoList.add(chaincodeConnection.getResourceInfo());
        }

        return resourceInfoList;
    }

    private Response handleCall(Request request) {
        ChaincodeConnection chaincodeConnection =
                chaincodeMap.get(request.getResourceInfo().getName());
        if (chaincodeConnection == null) {
            return null;
        }
        return chaincodeConnection.call(request);
    }

    private Response handleSendTransactioneEndorser(Request request) {
        ChaincodeConnection chaincodeConnection =
                chaincodeMap.get(request.getResourceInfo().getName());
        if (chaincodeConnection == null) {
            return null;
        }
        return chaincodeConnection.sendTransactionEndorser(request);
    }

    private Response handleSendTransactioneOrderer(Request request) {
        ChaincodeConnection chaincodeConnection =
                chaincodeMap.get(request.getResourceInfo().getName());
        if (chaincodeConnection == null) {
            return null;
        }
        return chaincodeConnection.sendTransactionOrderer(request);
    }

    private Response handleGetBlockNumber(Request request) {
        return null;
    }

    private Response handleGetBlockHeader(Request request) {
        return null;
    }

    public Channel getChannel() {
        return this.channel;
    }
}
