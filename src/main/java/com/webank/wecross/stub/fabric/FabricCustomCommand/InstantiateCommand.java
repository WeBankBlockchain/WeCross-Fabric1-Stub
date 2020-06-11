package com.webank.wecross.stub.fabric.FabricCustomCommand;

import com.webank.wecross.stub.fabric.InstantiateChaincodeRequest;

public class InstantiateCommand {
    public static final String NAME = "instantiate";
    public static final String DESCRIPTION =
            "Command:\tinstantiate [chaincodeNme] [version] [orgName] [channelName] [language(GO_LANG/JAVA)] [endorsementPolicy] [instantiateArgs]\n"
                    + "Return:\tString(Success or Failed)\n"
                    + "Eg:\n"
                    + "       \tinstantiate sacc 1.0 Org1 mychannel GO_LANG OufOf() [a, b, 10]";

    public static InstantiateChaincodeRequest parseArgs(Object[] args) throws Exception {
        check(args);

        String chaincodeName = (String) args[0];
        String version = (String) args[1];
        String orgName = (String) args[2];
        String channelName = (String) args[3];
        String language = (String) args[4];
        String endorsementPolicy = (String) args[5];
        String[] instantiateArgs = (String[]) args[6];

        InstantiateChaincodeRequest instantiateChaincodeRequest =
                InstantiateChaincodeRequest.build()
                        .setName(chaincodeName)
                        .setVersion(version)
                        .setOrgName(orgName)
                        .setChannelName(channelName)
                        .setChaincodeLanguage(language)
                        .setEndorsementPolicy(endorsementPolicy)
                        // .setTransientMap()
                        .setArgs(instantiateArgs);

        return instantiateChaincodeRequest;
    }

    private static void check(Object[] args) throws Exception {
        if (args.length != 7) {
            throw new Exception("Args length is not 7 but " + args.length + "\n" + DESCRIPTION);
        }
    }
}
