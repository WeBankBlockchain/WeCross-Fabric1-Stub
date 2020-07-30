package com.webank.wecross.stub.fabric;

/*
[common]
    name = 'fabric'
    type = 'Fabric1.4'

[fabricServices]
    channelName = 'mychannel'
    orgUserName = 'fabric_admin'
    orgUserAccountPath = 'classpath:accounts/fabric_admin'
    ordererTlsCaFile = 'orderer-tlsca.crt'
    ordererAddress = 'grpcs://localhost:7050'

[orgs]
    [orgs.org1]
         tlsCaFile = 'org1-tlsca.crt'
         adminName = 'fabric_admin_org1'
         peers = ['grpcs://localhost:7051']

    [orgs.org2]
         tlsCaFile = 'org2-tlsca.crt'
         adminName = 'fabric_admin_org1'
         peers = ['grpcs://localhost:9051']

 */

import com.moandjiezana.toml.Toml;
import com.webank.wecross.stub.fabric.proxy.ProxyChaincodeResource;
import com.webank.wecross.utils.FabricUtils;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FabricStubConfigParser {
    public static final long DEFAULT_PROPOSAL_WAIT_TIME = 80000; // ms
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
                throw new Exception("Stub config file not found.");
            }

            common = new Common(toml);
            fabricServices = new FabricServices(toml, stubPath);
            orgs = new Orgs(toml, stubPath);
            advanced = new Advanced(toml);

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

    public static class Common {
        /*
            [common]
            type = 'FABRIC'
        */
        private String type;

        public Common(Toml toml) throws Exception {
            type = parseString(toml, "common.type");
        }

        public String getType() {
            return type;
        }
    }

    public static class FabricServices {
        /*
        [fabricServices]
            channelName = 'mychannel'
            orgName = 'Org1'
            mspId = 'Org1MSP'
            orgUserName = 'fabric1'
            orgUserAccountPath = 'classpath:/accounts/fabric1'
            ordererTlsCaFile = 'ordererTlsCaFile'
            ordererAddress = 'grpcs://127.0.0.1:7050'
        */
        private String channelName;
        private String orgName;
        private String mspId;
        private String orgUserName;
        private String orgUserAccountPath;
        private String ordererTlsCaFile;
        private String ordererAddress;

        public FabricServices(Toml toml, String stubPath) throws Exception {
            channelName = parseString(toml, "fabricServices.channelName");
            orgUserName = parseString(toml, "fabricServices.orgUserName");
            orgUserAccountPath =
                    FabricUtils.getPath(parseString(toml, "fabricServices.orgUserAccountPath"));
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

        public String getOrgUserAccountPath() {
            return orgUserAccountPath;
        }

        public String getOrdererTlsCaFile() {
            return ordererTlsCaFile;
        }

        public String getOrdererAddress() {
            return ordererAddress;
        }
    }

    public static class Orgs {
        /*
        [orgs]
            [orgs.org1]
                tlsCaFile = 'org1-tlsca.crt'
                adminName = 'fabric_admin_org1'
                peers = ['grpcs://localhost:7051']

            [orgs.org2]
                tlsCaFile = 'org2-tlsca.crt'
                adminName = 'fabric_admin_org1'
                peers = ['grpcs://localhost:9051']
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

        public static class Org {
            /*
            [orgs.org2]
                tlsCaFile = 'org2-tlsca.crt'
                adminName = 'fabric_admin_org1'
                peers = ['grpcs://localhost:9051']
            */
            private String tlsCaFile;
            private String adminName;
            private List<String> peers;

            public Org(Map<String, Object> orgMap, String stubPath) throws Exception {
                tlsCaFile =
                        FabricUtils.getPath(
                                stubPath + File.separator + parseStringBase(orgMap, "tlsCaFile"));
                adminName = parseStringBase(orgMap, "adminName");
                peers = parseStringList(orgMap, "peers");
            }

            public String getTlsCaFile() {
                return tlsCaFile;
            }

            public String getAdminName() {
                return adminName;
            }

            public List<String> getPeers() {
                return peers;
            }
        }
    }

    public static class Advanced {
        /*
            [advanced]
            proxyChaincode = 'WeCrossProxy'
        * */
        private String proxyChaincode;

        public Advanced(Toml toml) throws Exception {
            proxyChaincode =
                    parseString(
                            toml, "advanced.proxyChaincode", ProxyChaincodeResource.DEFAULT_NAME);
        }

        public String getProxyChaincode() {
            return proxyChaincode;
        }
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
