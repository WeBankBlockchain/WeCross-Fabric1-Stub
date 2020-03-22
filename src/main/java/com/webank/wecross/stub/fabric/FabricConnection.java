package com.webank.wecross.stub.fabric;

import static com.webank.wecross.utils.FabricUtils.bytesToLong;
import static com.webank.wecross.utils.FabricUtils.longToBytes;

import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Request;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.Response;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.hyperledger.fabric.sdk.BlockInfo;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;

public class FabricConnection implements Connection {
    private HFClient hfClient;
    private Channel channel;
    private Map<String, ChaincodeConnection> chaincodeMap;
    private long latestBlockNumber = 0;

    public FabricConnection(
            HFClient hfClient, Channel channel, Map<String, ChaincodeConnection> chaincodeMap) {
        this.hfClient = hfClient;
        this.channel = channel;
        this.chaincodeMap = chaincodeMap;
    }

    public void start() throws Exception {
        channel.registerBlockListener(
                blockEvent -> {
                    long currentBlockNumber = blockEvent.getBlockNumber();
                    if (this.latestBlockNumber < currentBlockNumber) {
                        this.latestBlockNumber = currentBlockNumber;
                    }
                });

        channel.initialize();
    }

    @Override
    public Response send(Request request) {
        switch (request.getType()) {
            case FabricType.ConnectionMessage.FABRIC_CALL:
                return handleCall(request);

            case FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ENDORSER:
                return handleSendTransactionEndorser(request);

            case FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ORDERER:
                return handleSendTransactionOrderer(request);

            case FabricType.ConnectionMessage.FABRIC_GET_BLOCK_NUMBER:
                return handleGetBlockNumber(request);

            case FabricType.ConnectionMessage.FABRIC_GET_BLOCK_HEADER:
                return handleGetBlockHeader(request);

            default:
                return FabricConnectionResponse.build()
                        .errorCode(FabricType.ResponseStatus.RESOURCE_NOT_FOUND)
                        .errorMessage(
                                "Resource not found, name: " + request.getResourceInfo().getName());
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
        if (chaincodeConnection != null) {
            return chaincodeConnection.call(request);
        } else {
            return FabricConnectionResponse.build()
                    .errorCode(FabricType.ResponseStatus.RESOURCE_NOT_FOUND)
                    .errorMessage(
                            "Resource not found, name: " + request.getResourceInfo().getName());
        }
    }

    private Response handleSendTransactionEndorser(Request request) {
        ChaincodeConnection chaincodeConnection =
                chaincodeMap.get(request.getResourceInfo().getName());
        if (chaincodeConnection != null) {
            return chaincodeConnection.sendTransactionEndorser(request);
        } else {
            return FabricConnectionResponse.build()
                    .errorCode(FabricType.ResponseStatus.RESOURCE_NOT_FOUND)
                    .errorMessage(
                            "Resource not found, name: " + request.getResourceInfo().getName());
        }
    }

    private Response handleSendTransactionOrderer(Request request) {
        ChaincodeConnection chaincodeConnection =
                chaincodeMap.get(request.getResourceInfo().getName());
        if (chaincodeConnection != null) {
            return chaincodeConnection.sendTransactionOrderer(request);
        } else {
            return FabricConnectionResponse.build()
                    .errorCode(FabricType.ResponseStatus.RESOURCE_NOT_FOUND)
                    .errorMessage(
                            "Resource not found, name: " + request.getResourceInfo().getName());
        }
    }

    private Response handleGetBlockNumber(Request request) {
        byte[] numberBytes = longToBytes(latestBlockNumber);

        return FabricConnectionResponse.build()
                .errorCode(FabricType.ResponseStatus.SUCCESS)
                .errorMessage("Success")
                .data(numberBytes);
    }

    private Response handleGetBlockHeader(Request request) {

        Response response;
        try {
            long blockNumber = bytesToLong(request.getData());

            // Fabric Just return block
            BlockInfo blockInfo = channel.queryBlockByNumber(blockNumber);
            byte[] blockBytes = blockInfo.getBlock().toByteArray();

            response =
                    FabricConnectionResponse.build()
                            .errorCode(FabricType.ResponseStatus.SUCCESS)
                            .errorMessage("Success")
                            .data(blockBytes);

        } catch (Exception e) {
            response =
                    FabricConnectionResponse.build()
                            .errorCode(FabricType.ResponseStatus.INTERNAL_ERROR)
                            .errorMessage("Get block exception: " + e.getMessage());
        }
        return response;
    }

    public Channel getChannel() {
        return this.channel;
    }
}
