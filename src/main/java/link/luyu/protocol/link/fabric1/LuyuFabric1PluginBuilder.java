package link.luyu.protocol.link.fabric1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.wecross.account.FabricAccountFactory;
import com.webank.wecross.stub.Block;
import com.webank.wecross.stub.ObjectMapperFactory;
import com.webank.wecross.stub.Path;
import com.webank.wecross.stub.fabric.FabricStubFactory;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import link.luyu.protocol.link.Connection;
import link.luyu.protocol.link.Driver;
import link.luyu.protocol.link.LuyuPlugin;
import link.luyu.protocol.link.PluginBuilder;
import link.luyu.protocol.network.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@LuyuPlugin("Fabric1.4")
public class LuyuFabric1PluginBuilder extends PluginBuilder {
    private static Logger logger = LoggerFactory.getLogger(LuyuFabric1PluginBuilder.class);
    private FabricStubFactory stubFactory = new FabricStubFactory();
    private LuyuMemoryBlockManagerFactory memoryBlockManagerFactory =
            new LuyuMemoryBlockManagerFactory();
    private ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();
    private FabricAccountFactory accountFactory = new FabricAccountFactory();

    @Override
    public Connection newConnection(Map<String, Object> properties) {
        try {
            String chainPath = (String) properties.get("chainPath");
            String chainDir = (String) properties.get("chainDir");

            // use chains/<fabric-chain>/accounts/<account-name> instead of accounts/<account-name>
            // in luyu protocol
            Map<String, String> common = (Map<String, String>) properties.get("common");
            if (common == null) {
                throw new RuntimeException("[common] item not found in connection.toml");
            }

            common.put("accountsDir", "accounts");

            com.webank.wecross.stub.Connection wecrossConnection =
                    stubFactory.newConnection(properties);
            if (wecrossConnection == null || !hasConnectionReady(wecrossConnection)) {

                logger.error(
                        "newConnection error, properties: {}",
                        objectMapper.writeValueAsString(properties.toString()));

                return null;
            }

            LuyuConnectionAdapter luyuConnectionAdapter =
                    new LuyuConnectionAdapter(wecrossConnection);
            // parse resources

            ArrayList<Map<String, Object>> resources =
                    (ArrayList<Map<String, Object>>) properties.get("luyu-resources");
            if (resources != null) {
                for (Map<String, Object> resourceMap : resources) {
                    Path path = Path.decode(chainPath);
                    String name = (String) resourceMap.get("name");
                    if (name == null) {
                        throw new Exception("\"name\" item not found, please check config ");
                    }

                    path.setResource(name);

                    Resource resource = new Resource();
                    resource.setType("Fabric1.4");
                    resource.setPath(path.toString());

                    ArrayList<String> methods = (ArrayList<String>) resourceMap.get("methods");
                    if (methods != null) {
                        resource.setMethods(methods.toArray(new String[] {}));
                    }

                    luyuConnectionAdapter.addLuyuResourceConfig(name, resource);
                }
            }
            return luyuConnectionAdapter;
        } catch (Exception e) {
            logger.error(
                    "newConnection error, properties: {}, {}", properties.toString(), e.toString());
        }
        return null;
    }

    @Override
    public Driver newDriver(Connection connection, Map<String, Object> properties) {
        String chainPath = (String) properties.get("chainPath");
        com.webank.wecross.stub.Driver wecrossDriver = stubFactory.newDriver();
        LuyuWeCrossConnection luyuWeCrossConnection = new LuyuWeCrossConnection(connection);
        LuyuMemoryBlockManager blockManager =
                memoryBlockManagerFactory.build(chainPath, wecrossDriver, luyuWeCrossConnection);

        LuyuDriverAdapter luyuDriverAdapter =
                new LuyuDriverAdapter(
                        "Fabric1.4", chainPath, wecrossDriver, luyuWeCrossConnection, blockManager);
        blockManager.start();
        return luyuDriverAdapter;
    }

    private boolean hasConnectionReady(com.webank.wecross.stub.Connection connection) {
        try {
            com.webank.wecross.stub.Driver wecrossDriver = stubFactory.newDriver();
            CompletableFuture<Block> future = new CompletableFuture<>();

            wecrossDriver.asyncGetBlock(
                    0,
                    false,
                    connection,
                    new com.webank.wecross.stub.Driver.GetBlockCallback() {
                        @Override
                        public void onResponse(Exception e, Block block) {
                            if (e != null) {
                                future.complete(null);
                            } else {
                                future.complete(block);
                            }
                        }
                    });

            return future.get(30, TimeUnit.SECONDS) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
