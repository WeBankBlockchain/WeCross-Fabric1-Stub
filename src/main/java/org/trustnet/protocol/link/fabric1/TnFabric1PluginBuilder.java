package org.trustnet.protocol.link.fabric1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.wecross.account.FabricAccountFactory;
import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.Block;
import com.webank.wecross.stub.ObjectMapperFactory;
import com.webank.wecross.stub.Path;
import com.webank.wecross.stub.fabric.FabricStubFactory;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.trustnet.protocol.link.Connection;
import org.trustnet.protocol.link.Driver;
import org.trustnet.protocol.link.PluginBuilder;
import org.trustnet.protocol.link.TnPlugin;
import org.trustnet.protocol.network.Resource;

@TnPlugin("Fabric1.4")
public class TnFabric1PluginBuilder extends PluginBuilder {
    private static Logger logger = LoggerFactory.getLogger(TnFabric1PluginBuilder.class);
    private FabricStubFactory stubFactory = new FabricStubFactory();
    private TnMemoryBlockManagerFactory memoryBlockManagerFactory =
            new TnMemoryBlockManagerFactory();
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

            TnConnectionAdapter tnConnectionAdapter = new TnConnectionAdapter(wecrossConnection);
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

                    tnConnectionAdapter.addTnResourceConfig(name, resource);
                }
            }
            return tnConnectionAdapter;
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
        TnWeCrossConnection tnWeCrossConnection = new TnWeCrossConnection(connection);

        String verifierString = getVerifierString(properties);
        tnWeCrossConnection.setVerifierString(verifierString);

        TnMemoryBlockManager blockManager =
                memoryBlockManagerFactory.build(chainPath, wecrossDriver, tnWeCrossConnection);

        TnDriverAdapter tnDriverAdapter =
                new TnDriverAdapter(
                        "Fabric1.4", chainPath, wecrossDriver, tnWeCrossConnection, blockManager);
        blockManager.start();
        return tnDriverAdapter;
    }

    private String getVerifierString(Map<String, Object> properties) throws RuntimeException {

        String chainPath = (String) properties.get("chainPath");
        String chainDir = (String) properties.get("chainDir");
        String verifierKey = FabricType.FABRIC_VERIFIER.toLowerCase();
        try {
            if (properties.containsKey(verifierKey)) {

                Map<String, Object> verifierMap = (Map<String, Object>) properties.get(verifierKey);
                // add chainDir in verifierMap
                verifierMap.put("chainDir", chainDir);
                verifierMap.put("chainType", FabricType.STUB_NAME);

                ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();
                String verifierString = objectMapper.writeValueAsString(verifierMap);
                logger.info("Chain: " + chainPath + " configure verifier as: " + verifierString);

                return verifierString;
            } else {
                return null;
            }

        } catch (Exception e) {
            throw new RuntimeException("Parse [" + verifierKey + "] in driver.toml failed. " + e);
        }
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
