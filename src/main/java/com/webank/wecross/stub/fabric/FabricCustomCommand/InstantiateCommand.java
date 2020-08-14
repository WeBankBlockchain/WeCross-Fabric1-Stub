package com.webank.wecross.stub.fabric.FabricCustomCommand;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;

public class InstantiateCommand {
    public static final String NAME = "instantiate";
    public static final String DESCRIPTION =
            "Command:\tinstantiate [chaincodeNme] [version] [orgNames] [language(GO_LANG/JAVA)] [endorsementPolicy] [instantiateArgs]\n"
                    + "Return:\tString(Success or Failed)\n"
                    + "Eg:\n"
                    + "       \tinstantiate sacc 1.0 [Org1, Org2] GO_LANG OufOf() [a, b, 10]";

    private static ObjectMapper objectMapper = new ObjectMapper();

    // parse args from sdk
    public static InstantiateChaincodeRequest parseEncodedArgs(
            java.lang.Object[] encodedArgs, String channelName) throws Exception {

        if (encodedArgs.length != 6) {
            throw new Exception(
                    "InstantiateChaincodeRequest args length is not 6 but: " + encodedArgs.length);
        }

        Object[] args = {
            encodedArgs[0], // chaincodeName
            encodedArgs[1], // version
            asStringArray((String) encodedArgs[2]), // orgNames
            encodedArgs[3], // language
            encodedArgs[4], // endorsementPolicy
            asStringArray((String) encodedArgs[5]) // instantiateArgs
        };

        return parseArgs(args, channelName);
    }

    // parse args inside stub
    public static InstantiateChaincodeRequest parseArgs(Object[] args, String channelName)
            throws Exception {
        check(args, channelName);

        String chaincodeName = (String) args[0];
        String version = (String) args[1];
        String[] orgNames = (String[]) args[2];
        String language = (String) args[3];
        String endorsementPolicy = (String) args[4];
        String[] instantiateArgs = (String[]) args[5];

        InstantiateChaincodeRequest instantiateChaincodeRequest =
                InstantiateChaincodeRequest.build()
                        .setName(chaincodeName)
                        .setVersion(version)
                        .setOrgNames(orgNames)
                        .setChannelName(channelName)
                        .setChaincodeLanguage(language)
                        .setEndorsementPolicy(endorsementPolicy)
                        // .setTransientMap()
                        .setArgs(instantiateArgs);

        return instantiateChaincodeRequest;
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
