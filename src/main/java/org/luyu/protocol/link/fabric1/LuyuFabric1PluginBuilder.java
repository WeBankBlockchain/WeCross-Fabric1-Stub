package org.luyu.protocol.link.fabric1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.wecross.account.FabricAccountFactory;
import com.webank.wecross.stub.Account;
import com.webank.wecross.stub.ObjectMapperFactory;
import com.webank.wecross.stub.Path;
import com.webank.wecross.stub.fabric.FabricStubFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import org.luyu.protocol.link.Connection;
import org.luyu.protocol.link.Driver;
import org.luyu.protocol.link.LuyuPlugin;
import org.luyu.protocol.link.PluginBuilder;
import org.luyu.protocol.network.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@LuyuPlugin("Fabric1.4")
public class LuyuFabric1PluginBuilder implements PluginBuilder {
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

            common.put(
                    "accountsDir",
                    chainDir.replaceAll("classpath:", "") + File.separator + "accounts");

            com.webank.wecross.stub.Connection wecrossConnection =
                    stubFactory.newConnection(properties);
            if (wecrossConnection == null) {

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

        Map<String, String> users = (Map<String, String>) properties.get("users");
        if (users == null) {
            throw new RuntimeException("[users] adminName not set");
        }

        String adminName = (String) users.get("adminName");
        if (adminName == null) {
            throw new RuntimeException("[users] adminName not set");
        }

        String chainDir = (String) properties.get("chainDir");
        Account admin =
                FabricAccountFactory.build(
                        adminName,
                        chainDir + File.separator + "accounts" + File.separator + adminName);

        LuyuDriverAdapter luyuDriverAdapter =
                new LuyuDriverAdapter(
                        "Fabric1.4",
                        chainPath,
                        wecrossDriver,
                        luyuWeCrossConnection,
                        blockManager,
                        accountFactory,
                        admin);
        blockManager.start();
        return luyuDriverAdapter;
    }
}
