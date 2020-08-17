package com.webank.wecross.stub.fabric.FabricCustomCommand;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;

public class UpgradeCommand {
    public static final String NAME = "upgrade";
    public static final String DESCRIPTION =
            "Command:\tupgrade [chaincodeNme] [version] [orgNames] [language(GO_LANG/JAVA)] [endorsementPolicy] [upgradeArgs]\n"
                    + "Return:\tString(Success or Failed)\n"
                    + "Eg:\n"
                    + "       \tupgrade sacc 1.0 [Org1, Org2] GO_LANG OufOf() [a, b, 10]";

    private static ObjectMapper objectMapper = new ObjectMapper();

    // parse args from sdk
    public static UpgradeChaincodeRequest parseEncodedArgs(Object[] encodedArgs, String channelName)
            throws Exception {

        if (encodedArgs.length != 6) {
            throw new Exception(
                    "UpgradeChaincodeRequest args length is not 6 but: " + encodedArgs.length);
        }

        Object[] args = {
            encodedArgs[0], // chaincodeName
            encodedArgs[1], // version
            asStringArray((String) encodedArgs[2]), // orgNames
            encodedArgs[3], // language
            encodedArgs[4], // endorsementPolicy
            asStringArray((String) encodedArgs[5]) // upgradeArgs
        };

        return parseArgs(args, channelName);
    }

    // parse args inside stub
    public static UpgradeChaincodeRequest parseArgs(Object[] args, String channelName)
            throws Exception {
        check(args, channelName);

        String chaincodeName = (String) args[0];
        String version = (String) args[1];
        String[] orgNames = (String[]) args[2];
        String language = (String) args[3];
        String endorsementPolicy = (String) args[4];
        String[] upgradeArgs = (String[]) args[5];

        UpgradeChaincodeRequest upgradeChaincodeRequest =
                UpgradeChaincodeRequest.build()
                        .setName(chaincodeName)
                        .setVersion(version)
                        .setOrgNames(orgNames)
                        .setChannelName(channelName)
                        .setChaincodeLanguage(language)
                        .setEndorsementPolicy(endorsementPolicy)
                        // .setTransientMap()
                        .setArgs(upgradeArgs);

        return upgradeChaincodeRequest;
    }

    private static void check(Object[] args, String channelName) throws Exception {
        if (args.length != 6) {
            throw new Exception("Args length is not 6 but " + args.length + "\n" + DESCRIPTION);
        }

        if (channelName == null) {
            throw new Exception("ChannelName is null");
        }
    }

    private static String[] asStringArray(String content) throws Exception {
        ArrayList<String> arrayList =
                objectMapper.readValue(content, new TypeReference<ArrayList<String>>() {});
        return arrayList.toArray(new String[] {});
    }
}
