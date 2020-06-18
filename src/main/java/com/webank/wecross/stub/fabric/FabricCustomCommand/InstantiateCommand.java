package com.webank.wecross.stub.fabric.FabricCustomCommand;

import com.webank.wecross.stub.fabric.InstantiateChaincodeRequest;

public class InstantiateCommand {
    public static final String NAME = "instantiate";
    public static final String DESCRIPTION =
            "Command:\tinstantiate [chaincodeNme] [version] [orgName] [language(GO_LANG/JAVA)] [endorsementPolicy] [instantiateArgs]\n"
                    + "Return:\tString(Success or Failed)\n"
                    + "Eg:\n"
                    + "       \tinstantiate sacc 1.0 Org1 GO_LANG OufOf() [a, b, 10]";

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
}
