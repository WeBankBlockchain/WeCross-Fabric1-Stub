package com.webank.wecross.stub.fabric;

/*
[common]
    stub = 'fabric'
    type = 'FABRIC'

[fabricServices]
    channelName = 'mychannel'
    orgName = 'Org1'
    mspId = 'Org1MSP'
    orgUserName = 'fabric1'
    orgUserAccountPath = 'classpath:/accounts/fabric1'
    ordererTlsCaFile = 'classpath:/stubs/fabric/ordererTlsCaFile'
    ordererAddress = 'grpcs://127.0.0.1:7050'

[peers]
    [peers.org1]
        peerTlsCaFile = 'classpath:/stubs/fabric/peerOrg1CertFile'
        peerAddress = 'grpcs://127.0.0.1:7051'
    [peers.org2]
         peerTlsCaFile = 'classpath:/stubs/fabric/peerOrg2CertFile'
         peerAddress = 'grpcs://127.0.0.1:9051'

# resources is a list
[[resources]]
    # name cannot be repeated
    name = 'HelloWeCross'
    type = 'FABRIC_CONTRACT'
    chainCodeName = 'mycc'
    chainLanguage = "go"
    peers=['org1','org2']
[[resources]]
    name = 'HelloWorld'
    type = 'FABRIC_CONTRACT'
    chainCodeName = 'mygg'
    chainLanguage = "go"
    peers=['org1','org2']
 */

import com.moandjiezana.toml.Toml;
import com.webank.wecross.utils.FabricUtils;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class FabricStubConfigParser {
    public static final long DEFAULT_PROPOSAL_WAIT_TIME = 120000; // ms

    private Common common;
    private FabricServices fabricServices;
    private Peers peers;
    private Resources resources;

    public FabricStubConfigParser(String stubPath) throws Exception {

        try {
            Toml toml;
            try {
                toml = FabricUtils.readToml(stubPath);
            } catch (Exception e) {
                throw new Exception("Stub config file not found.");
            }

            common = new Common(toml);
            fabricServices = new FabricServices(toml);
            peers = new Peers(toml);
            resources = new Resources(toml);

        } catch (Exception e) {
            throw new Exception(stubPath + " error: " + e);
        }
    }

    public Common getCommon() {
        return common;
    }

    public FabricServices getFabricServices() {
        return fabricServices;
    }

    public Map<String, Peers.Peer> getPeers() {
        return peers.getPeers();
    }

    public List<Resources.Resource> getResources() {
        return resources.getResources();
    }

    public static class Common {
        /*
            [common]
            stub = 'fabric'
            type = 'FABRIC'
        */
        private String stub;
        private String type;

        public Common(Toml toml) throws Exception {
            stub = parseString(toml, "common.stub");
            type = parseString(toml, "common.type");
        }

        public String getStub() {
            return stub;
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
            ordererTlsCaFile = 'classpath:/stubs/fabric/ordererTlsCaFile'
            ordererAddress = 'grpcs://127.0.0.1:7050'
        */

        private String channelName;
        private String orgName;
        private String mspId;
        private String orgUserName;
        private String orgUserAccountPath;
        private String ordererTlsCaFile;
        private String ordererAddress;

        public FabricServices(Toml toml) throws Exception {
            channelName = parseString(toml, "fabricServices.channelName");
            orgName = parseString(toml, "fabricServices.orgName");
            mspId = parseString(toml, "fabricServices.mspId");
            orgUserName = parseString(toml, "fabricServices.orgUserName");
            orgUserAccountPath =
                    FabricUtils.getPath(parseString(toml, "fabricServices.orgUserAccountPath"));
            ordererTlsCaFile =
                    FabricUtils.getPath(parseString(toml, "fabricServices.ordererTlsCaFile"));
            ordererAddress = parseString(toml, "fabricServices.ordererAddress");
        }

        public String getChannelName() {
            return channelName;
        }

        public String getOrgName() {
            return orgName;
        }

        public String getMspId() {
            return mspId;
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

    public static class Peers {
        /*
            [peers]
                [peers.org1]
                    peerTlsCaFile = 'classpath:/stubs/fabric/peerOrg1CertFile'
                    peerAddress = 'grpcs://127.0.0.1:7051'
                [peers.org2]
                     peerTlsCaFile = 'classpath:/stubs/fabric/peerOrg2CertFile'
                     peerAddress = 'grpcs://127.0.0.1:9051'
        */

        private Map<String, Peer> peers = new HashMap<>();

        public Peers(Toml toml) throws Exception {
            @SuppressWarnings("unchecked")
            Map<String, Map<String, String>> peersMaps =
                    (Map<String, Map<String, String>>) toml.toMap().get("peers");
            if (peersMaps == null) {
                String errorMessage = "\" + peers \" item illegal";
                ;
                throw new Exception(errorMessage);
            }

            for (String peerName : peersMaps.keySet()) {
                try {
                    peers.put(peerName, new Peer(peersMaps.get(peerName)));
                } catch (Exception e) {
                    throw new Exception("\"" + peerName + "\"." + e.getMessage());
                }
            }
        }

        public Map<String, Peer> getPeers() {
            return peers;
        }

        public static class Peer {
            private String peerTlsCaFile;
            private String peerAddress;

            public Peer(Map<String, String> peerMap) throws Exception {
                peerTlsCaFile = FabricUtils.getPath(parseString(peerMap, "peerTlsCaFile"));
                peerAddress = parseString(peerMap, "peerAddress");
            }

            public String getPeerTlsCaFile() {
                return peerTlsCaFile;
            }

            public String getPeerAddress() {
                return peerAddress;
            }
        }
    }

    public static class Resources {
        /*
            # resources is a list
            [[resources]]
                # name cannot be repeated
                name = 'HelloWeCross'
                type = 'FABRIC_CONTRACT'
                chainCodeName = 'mycc'
                chainLanguage = "go"
                peers=['org1','org2']
            [[resources]]
                name = 'HelloWorld'
                type = 'FABRIC_CONTRACT'
                chainCodeName = 'mygg'
                chainLanguage = "go"
                peers=['org1','org2']
        * */
        private List<Resource> resources = new LinkedList<>();

        public Resources(Toml toml) throws Exception {
            @SuppressWarnings("unchecked")
            List<Object> resourcesList = toml.getList("resources");
            if (resourcesList == null) {
                String errorMessage = "\" + resources \" item illegal";
                ;
                throw new Exception(errorMessage);
            }

            for (Object resourceMap : resourcesList) {
                resources.add(new Resource((Map<String, Object>) resourceMap));
            }
        }

        private List<Resource> getResources() {
            return resources;
        }

        public static class Resource {
            private String name;
            private String type;
            private String chainCodeName;
            private String chainLanguage;
            private List<String> peers;
            private Long proposalWaitTime = DEFAULT_PROPOSAL_WAIT_TIME;

            public Resource(Map<String, Object> map) throws Exception {
                name = parseStringBase(map, "name");
                type = parseStringBase(map, "type");
                chainCodeName = parseStringBase(map, "chainCodeName");
                chainLanguage = parseStringBase(map, "chainLanguage");
                peers = parseStringList(map, "peers");

                Long proposalWaitTimeTmp = (Long) map.get("proposalWaitTime");
                if (proposalWaitTimeTmp != null) {
                    proposalWaitTime = proposalWaitTimeTmp.longValue();
                }
            }

            public String getName() {
                return name;
            }

            public String getType() {
                return type;
            }

            public String getChainCodeName() {
                return chainCodeName;
            }

            public String getChainLanguage() {
                return chainLanguage;
            }

            public List<String> getPeers() {
                return peers;
            }

            public Long getProposalWaitTime() {
                return proposalWaitTime;
            }
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
