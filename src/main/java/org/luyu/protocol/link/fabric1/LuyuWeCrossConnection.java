package org.luyu.protocol.link.fabric1;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.ObjectMapperFactory;
import com.webank.wecross.stub.Request;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.Response;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LuyuWeCrossConnection implements Connection {
    private static Logger logger = LoggerFactory.getLogger(LuyuWeCrossConnection.class);
    private ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();
    private org.luyu.protocol.link.Connection luyuConnection;
    private Map<String, String> properties;

    public LuyuWeCrossConnection(org.luyu.protocol.link.Connection luyuConnection) {
        this.luyuConnection = luyuConnection;
    }

    @Override
    public void asyncSend(Request request, Callback callback) {
        try {
            byte[] requestBytes = objectMapper.writeValueAsBytes(request);

            luyuConnection.asyncSend(
                    request.getPath(),
                    request.getType(),
                    requestBytes,
                    new org.luyu.protocol.link.Connection.Callback() {
                        @Override
                        public void onResponse(int errorCode, String message, byte[] responseData) {
                            Response response = new Response();
                            response.setErrorCode(errorCode);
                            response.setErrorMessage(message);
                            response.setData(responseData);
                            callback.onResponse(response);
                        }
                    });
        } catch (Exception e) {
            Response response = new Response();
            response.setErrorCode(FabricType.TransactionResponseStatus.REQUEST_ENCODE_EXCEPTION);
            response.setErrorMessage("LuyuWeCrossConnection encode exception: " + e.getMessage());
            response.setData(null);
            callback.onResponse(response);
        }
    }

    @Override
    public void setConnectionEventHandler(ConnectionEventHandler eventHandler) {

        luyuConnection.subscribe(
                LuyuConnectionAdapter.ON_RESOURCES_CHANGE,
                new byte[0],
                new org.luyu.protocol.link.Connection.Callback() {
                    @Override
                    public void onResponse(int errorCode, String message, byte[] responseData) {
                        if (errorCode != LuyuConnectionAdapter.SUCCESS) {
                            logger.warn(
                                    "On subscribed message(ON_RESOURCES_CHANGE) error, code:{}, message:{}",
                                    errorCode,
                                    message);
                            return;
                        }

                        try {
                            List<ResourceInfo> resourceInfos = new LinkedList<>();
                            resourceInfos =
                                    objectMapper.readValue(
                                            responseData,
                                            new TypeReference<List<ResourceInfo>>() {});

                            eventHandler.onResourcesChange(resourceInfos);
                        } catch (Exception e) {
                            logger.warn(
                                    "On subscribed message(ON_RESOURCES_CHANGE) decode exception {}",
                                    e);
                        }
                    }
                });
    }

    @Override
    public Map<String, String> getProperties() {
        if (properties != null) {
            return properties;
        }

        try {
            CompletableFuture<byte[]> future = new CompletableFuture<>();
            luyuConnection.asyncSend(
                    "",
                    LuyuDefault.GET_PROPERTIES,
                    new byte[] {},
                    new org.luyu.protocol.link.Connection.Callback() {
                        @Override
                        public void onResponse(int errorCode, String message, byte[] responseData) {
                            if (errorCode != 0) {
                                logger.warn(
                                        "getProperties failed, status: {}, message: {}",
                                        errorCode,
                                        message);
                                future.complete(null);
                            } else {
                                future.complete(responseData);
                            }
                        }
                    });
            byte[] propertiesBytes =
                    future.get(LuyuDefault.ADAPTER_QUERY_EXPIRES, TimeUnit.SECONDS);
            Map<String, String> retProperties = new HashMap<>();
            retProperties = objectMapper.readValue(propertiesBytes, retProperties.getClass());
            properties = retProperties;
            return properties;
        } catch (Exception e) {
            logger.warn("getProperties exception: ", e);
        }

        return null;
    }
}
