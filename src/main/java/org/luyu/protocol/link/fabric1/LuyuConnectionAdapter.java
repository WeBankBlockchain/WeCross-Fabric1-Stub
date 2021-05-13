package org.luyu.protocol.link.fabric1;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.ObjectMapperFactory;
import com.webank.wecross.stub.Request;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.Response;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.luyu.protocol.link.Connection;
import org.luyu.protocol.network.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LuyuConnectionAdapter implements Connection {
    private static Logger logger = LoggerFactory.getLogger(LuyuConnectionAdapter.class);

    private com.webank.wecross.stub.Connection wecrossConnection;
    private static ExecutorService executor = Executors.newFixedThreadPool(1);
    private ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();
    private Collection<Resource> resources = new HashSet<>();

    public static int SUCCESS = 0;
    public static int ON_RESOURCES_CHANGE = 1001;

    public LuyuConnectionAdapter(com.webank.wecross.stub.Connection wecrossConnection) {
        this.wecrossConnection = wecrossConnection;
    }

    @Override
    public void asyncSend(String path, int type, byte[] data, Callback callback) {

        if (type == LuyuDefault.GET_PROPERTIES) {
            handleGetProperties(callback);
        } else if (type == LuyuDefault.LIST_RESOURCES) {
            handleListResources(callback);
        } else {
            handleNormalSend(path, type, data, callback);
        }
    }

    @Override
    public void subscribe(int type, byte[] data, Callback callback) {
        if (type == ON_RESOURCES_CHANGE) {

            wecrossConnection.setConnectionEventHandler(
                    new com.webank.wecross.stub.Connection.ConnectionEventHandler() {
                        @Override
                        public void onResourcesChange(List<ResourceInfo> resourceInfos) {
                            try {
                                byte[] resourceInfosBytes =
                                        objectMapper.writeValueAsBytes(resourceInfos);
                                callback.onResponse(SUCCESS, "success", resourceInfosBytes);
                            } catch (Exception e) {
                                logger.error("Handle ON_RESOURCES_CHANGE event exception: ", e);
                            }
                        }
                    });
        } else {
            logger.error("Unrecognized subscribe type: {}", type);
        }
    }

    private void handleNormalSend(String path, int type, byte[] data, Callback callback) {
        Request request = new Request();
        try {
            request = objectMapper.readValue(data, new TypeReference<Request>() {});

        } catch (Exception e) {
            callback.onResponse(
                    FabricType.TransactionResponseStatus.REQUEST_DECODE_EXCEPTION,
                    "LuyuConnectionAdapter decode exception" + e.getMessage(),
                    null);
            return;
        }
        wecrossConnection.asyncSend(
                request,
                new com.webank.wecross.stub.Connection.Callback() {
                    @Override
                    public void onResponse(Response response) {
                        callback.onResponse(
                                response.getErrorCode(),
                                response.getErrorMessage(),
                                response.getData());
                    }
                });
    }

    private void handleGetProperties(Callback callback) {
        try {
            Map<String, String> properties = wecrossConnection.getProperties();
            byte[] propertiesBytes = objectMapper.writeValueAsBytes(properties);

            executor.submit(
                    () -> {
                        callback.onResponse(
                                FabricType.TransactionResponseStatus.SUCCESS,
                                "success",
                                propertiesBytes);
                    });
        } catch (Exception e) {
            executor.submit(
                    () -> {
                        callback.onResponse(
                                FabricType.TransactionResponseStatus.GET_PROPERTIES_FAILED,
                                e.getMessage(),
                                new byte[] {});
                    });
        }
    }

    private void handleListResources(Callback callback) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(resources);

            executor.submit(
                    () -> {
                        callback.onResponse(
                                FabricType.TransactionResponseStatus.SUCCESS, "success", bytes);
                    });
        } catch (Exception e) {
            executor.submit(
                    () -> {
                        callback.onResponse(
                                FabricType.TransactionResponseStatus.RESOURCE_NOT_FOUND,
                                e.getMessage(),
                                new byte[] {});
                    });
        }
    }

    public void addResource(Resource resource) {
        resources.add(resource);
    }
}
