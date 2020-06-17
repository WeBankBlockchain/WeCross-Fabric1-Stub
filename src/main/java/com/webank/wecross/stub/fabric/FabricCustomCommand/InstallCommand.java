package com.webank.wecross.stub.fabric.FabricCustomCommand;

import com.webank.wecross.stub.fabric.InstallChaincodeRequest;

public class InstallCommand {

    public static final String NAME = "install";
    public static final String DESCRIPTION =
            "Command:\tinstall [chaincodeName] [version] [orgName] [language(GO_LANG/JAVA)] [code(generate with generateTarGzInputStreamBytes() function)]\n"
                    + "Return:\tString(Success or Failed)\n"
                    + "Eg:\n"
                    + "       \tinstall sacc 1.0 Org1 GO_LANG aef123d2s....";

    public static InstallChaincodeRequest parseArgs(java.lang.Object[] args, String channelName)
            throws Exception {
        check(args, channelName);

        String chaincodeName = (String) args[0];
        String version = (String) args[1];
        String orgName = (String) args[2];
        String language = (String) args[3];
        byte[] code = (byte[]) args[4];

        InstallChaincodeRequest installChaincodeRequest =
                InstallChaincodeRequest.build()
                        .setName(chaincodeName)
                        .setVersion(version)
                        .setOrgName(orgName)
                        .setChannelName(channelName)
                        .setChaincodeLanguage(language)
                        .setCode(code);
        return installChaincodeRequest;
    }

    private static void check(java.lang.Object[] args, String channelName) throws Exception {
        if (args.length != 5) {
            throw new Exception("Args length is not 5 but " + args.length + "\n" + DESCRIPTION);
        }

        if (channelName == null) {
            throw new Exception("ChannelName is null");
        }
    }
}
