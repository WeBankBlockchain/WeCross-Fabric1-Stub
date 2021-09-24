package com.webank.wecross.stub.fabric;

/*
[common]
    name = 'fabric'
    type = 'Fabric1.4'

[fabricServices]
    channelName = 'mychannel'
    orgUserName = 'fabric_admin'
    ordererTlsCaFile = 'orderer-tlsca.crt'
    ordererAddress = 'grpcs://localhost:7050'

[orgs]
    [orgs.org1]
        tlsCaFile = 'org1-tlsca.crt'
        adminName = 'fabric_admin_org1'
        endorsers = ['grpcs://localhost:7051']

    [orgs.org2]
        tlsCaFile = 'org2-tlsca.crt'
        adminName = 'fabric_admin_org1'
        endorsers = ['grpcs://localhost:9051']
[advanced]
    [advanced.threadPool]
        corePoolSize = 200
        maxPoolSize = 500
        queueCapacity = 5000
 */

import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import com.webank.wecross.utils.FabricUtils;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FabricStubConfigParser {
    private static Logger logger = LoggerFactory.getLogger(FabricStubConfigParser.class);

    public static final long DEFAULT_PROPOSAL_WAIT_TIME = 300000; // ms
    private String stubPath;

    private Common common;
    private FabricServices fabricServices;
    private Orgs orgs;
    private Advanced advanced;

    public FabricStubConfigParser(String stubPath) throws Exception {
        this.stubPath = stubPath;
        String stubConfig = stubPath + File.separator + "stub.toml";
        try {
            Toml toml;
            try {
                toml = FabricUtils.readToml(stubConfig);
            } catch (Exception e) {
                throw new Exception("Stub config file: " + stubConfig + " not found.");
            }

            common = new Common(toml);
            fabricServices = new FabricServices(toml, stubPath);
            orgs = new Orgs(toml, stubPath);
            advanced = new Advanced(toml);

            logger.info("Load config: {} with {}", stubConfig, this.toString());

        } catch (Exception e) {
            throw new Exception(stubConfig + " error: " + e);
        }
    }

    public FabricStubConfigParser(String stubPath, String configFileName) throws Exception {

        this.stubPath = stubPath;
        String stubConfig = stubPath + File.separator + configFileName;
        try {
            Toml toml;
            try {
                toml = FabricUtils.readToml(stubConfig);
            } catch (Exception e) {
                throw new Exception("Stub config file: " + stubConfig + " not found.");
            }

            common = new Common(toml);
            fabricServices = new FabricServices(toml, stubPath);
            orgs = new Orgs(toml, stubPath);
            advanced = new Advanced(toml);

            logger.info("Load config: {} with {}", stubConfig, this.toString());

        } catch (Exception e) {
            throw new Exception(stubConfig + " error: " + e);
        }
    }

    public FabricStubConfigParser(Map<String, Object> stubConfig) throws Exception {
        try {
            this.stubPath = (String) stubConfig.get("chainDir");
            if (this.stubPath == null) {
                throw new Exception("chainDir not set");
            }

            TomlWriter tomlWriter = new TomlWriter();
            String tomlString = tomlWriter.write(stubConfig);
            Toml toml = new Toml().read(tomlString);

            common = new Common(toml);
            fabricServices = new FabricServices(toml, stubPath);
            orgs = new Orgs(toml, stubPath);
            advanced = new Advanced(toml);

            logger.info("Load config(memory): {} with {}", stubPath, this.toString());
        } catch (Exception e) {
            throw new Exception(stubConfig + " error: " + e);
        }
    }

    public Common getCommon() {
        return common;
    }

    public FabricServices getFabricServices() {
        return fabricServices;
    }

    public Map<String, Orgs.Org> getOrgs() {
        return orgs.getOrgs();
    }

    public Advanced getAdvanced() {
        return advanced;
    }

    @Override
    public String toString() {
        return "FabricStubConfigParser{"
                + "stubPath='"
                + stubPath
                + '\''
                + ", common="
                + common
                + ", fabricServices="
                + fabricServices
                + ", orgs="
                + orgs
                + ", advanced="
                + advanced
                + '}';
    }

    public static class Common {
        /*
            [common]
            type = 'FABRIC'
            accountsDir = "accounts"
        */
        private String type;

        private String accountsDir;

        public Common(Toml toml) throws Exception {
            type = parseString(toml, "common.type");
            accountsDir = parseString(toml, "common.accountsDir", "accounts");
        }

        public String getType() {
            return type;
        }

        public String getAccountsDir() {
            return accountsDir;
        }

        @Override
        public String toString() {
            return "Common{" + "type='" + type + '\'' + '}';
        }
    }

    public static class FabricServices {
        /*
        [fabricServices]
            channelName = 'mychannel'
            orgUserName = 'fabric1'
            ordererTlsCaFile = 'ordererTlsCaFile'
            ordererAddress = 'grpcs://127.0.0.1:7050'
        */
        private String channelName;
        private String orgUserName;
        private String ordererTlsCaFile;
        private String ordererAddress;

        public FabricServices(Toml toml, String stubPath) throws Exception {
            channelName = parseString(toml, "fabricServices.channelName");
            orgUserName = parseString(toml, "fabricServices.orgUserName");
            ordererTlsCaFile =
                    FabricUtils.getPath(
                            stubPath
                                    + File.separator
                                    + parseString(toml, "fabricServices.ordererTlsCaFile"));
            ordererAddress = parseString(toml, "fabricServices.ordererAddress");
        }

        public String getChannelName() {
            return channelName;
        }

        public String getOrgUserName() {
            return orgUserName;
        }

        public String getOrdererTlsCaFile() {
            return ordererTlsCaFile;
        }

        public String getOrdererAddress() {
            return ordererAddress;
        }

        @Override
        public String toString() {
            return "FabricServices{"
                    + "channelName='"
                    + channelName
                    + '\''
                    + ", orgUserName='"
                    + orgUserName
                    + '\''
                    + ", ordererTlsCaFile='"
                    + ordererTlsCaFile
                    + '\''
                    + ", ordererAddress='"
                    + ordererAddress
                    + '\''
                    + '}';
        }
    }

    public static class Orgs {
        /*
        [orgs]
            [orgs.org1]
                tlsCaFile = 'org1-tlsca.crt'
                adminName = 'fabric_admin_org1'
                endorsers = ['grpcs://localhost:7051']

            [orgs.org2]
                tlsCaFile = 'org2-tlsca.crt'
                adminName = 'fabric_admin_org1'
                endorsers = ['grpcs://localhost:9051']
        */
        private Map<String, Org> orgs = new HashMap<>();

        public Orgs(Toml toml, String stubPath) throws Exception {
            Map<String, Map<String, Object>> orgsMap =
                    (Map<String, Map<String, Object>>) toml.toMap().get("orgs");
            if (orgsMap == null) {
                String errorMessage = "\" + orgs \" item illegal";

                throw new Exception(errorMessage);
            }

            for (String orgName : orgsMap.keySet()) {
                orgs.put(orgName, new Org(orgsMap.get(orgName), stubPath));
            }
        }

        public Map<String, Org> getOrgs() {
            return orgs;
        }

        @Override
        public String toString() {
            return "Orgs{" + "orgs=" + orgs.toString() + '}';
        }

        public static class Org {
            /*
            [orgs.org2]
                tlsCaFile = 'org2-tlsca.crt'
                adminName = 'fabric_admin_org1'
                endorsers = ['grpcs://localhost:9051']
            */
            private String tlsCaFile;
            private String adminName;
            private List<String> endorsers;

            public Org(Map<String, Object> orgMap, String stubPath) throws Exception {
                tlsCaFile =
                        FabricUtils.getPath(
                                stubPath + File.separator + parseStringBase(orgMap, "tlsCaFile"));
                adminName = parseStringBase(orgMap, "adminName", "");
                endorsers = parseStringList(orgMap, "endorsers");
            }

            public String getTlsCaFile() {
                return tlsCaFile;
            }

            public String getAdminName() {
                return adminName;
            }

            public List<String> getEndorsers() {
                return endorsers;
            }

            @Override
            public String toString() {
                return "Org{"
                        + "tlsCaFile='"
                        + tlsCaFile
                        + '\''
                        + ", adminName='"
                        + adminName
                        + '\''
                        + ", endorsers="
                        + endorsers
                        + '}';
            }
        }
    }

    public static class Advanced {
        /*
            [advanced]
                [advanced.threadPool]
                    corePoolSize = 200
                    maxPoolSize = 500
                    queueCapacity = 5000
        * */
        private ThreadPool threadPool;

        public Advanced(Toml toml) throws Exception {
            threadPool = new ThreadPool(toml);
        }

        public ThreadPool getThreadPool() {
            return threadPool;
        }

        @Override
        public String toString() {
            return "Advanced{" + "threadPool=" + threadPool + '}';
        }

        public static class ThreadPool {
            private int corePoolSize; // default
            private int maxPoolSize; // default
            private int queueCapacity; // default

            public ThreadPool(Toml toml) {
                corePoolSize = parseInt(toml, "advanced.threadPool.corePoolSize", 32);
                maxPoolSize = parseInt(toml, "advanced.threadPool.maxPoolSize", 32);
                queueCapacity = parseInt(toml, "advanced.threadPool.queueCapacity", 10000);
            }

            public int getCorePoolSize() {
                return corePoolSize;
            }

            public int getMaxPoolSize() {
                return maxPoolSize;
            }

            public int getQueueCapacity() {
                return queueCapacity;
            }
        }
    }

    private static int parseInt(Toml toml, String key, int defaultReturn) {
        Long res = toml.getLong(key);

        if (res == null) {
            logger.info(key + " has not set, default to " + defaultReturn);
            return defaultReturn;
        }
        return res.intValue();
    }

    private static String parseString(Toml toml, String key, String defaultReturn) {
        try {
            return parseString(toml, key);
        } catch (Exception e) {
            return defaultReturn;
        }
    }

    private static String parseString(Toml toml, String key) throws Exception {
        String res = toml.getString(key);

        if (res == null) {
            String errorMessage = "\"" + key + "\" item not found";
            throw new Exception(errorMessage);
        }
        return res;
    }

    private static String parseString(Map<String, String> map, String key) throws Exception {
        String res = map.get(key);

        if (res == null) {
            String errorMessage = "\"" + key + "\" item not found";
            throw new Exception(errorMessage);
        }
        return res;
    }

    private static String parseStringBase(Map<String, Object> map, String key) throws Exception {
        @SuppressWarnings("unchecked")
        String res = (String) map.get(key);

        if (res == null) {
            String errorMessage = "\"" + key + "\" item not found";
            throw new Exception(errorMessage);
        }
        return res;
    }

    private static String parseStringBase(
            Map<String, Object> map, String key, String defaultReturn) {
        @SuppressWarnings("unchecked")
        String res = (String) map.get(key);

        if (res == null) {
            return defaultReturn;
        }
        return res;
    }

    private static List<String> parseStringList(Map<String, Object> map, String key)
            throws Exception {
        @SuppressWarnings("unchecked")
        List<String> res = (List<String>) map.get(key);

        if (res == null) {
            String errorMessage = "\"" + key + "\" item illegal";
            throw new Exception(errorMessage);
        }
        return res;
    }
}
