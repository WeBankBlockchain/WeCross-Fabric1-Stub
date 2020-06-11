package com.webank.wecross.stub.fabric.FabricCustomCommand;

import com.webank.wecross.stub.fabric.InstallChaincodeRequest;

public class InstallCommand {

    public static final String NAME = "install";
    public static final String DESCRIPTION =
            "Command:\tinstall [chaincodeName] [version] [orgName] [channelName] [language(GO_LANG/JAVA)] [code(generate with generateTarGzInputStreamBytes() function)]\n"
                    + "Return:\tString(Success or Failed)\n"
                    + "Eg:\n"
                    + "       \tinstall sacc 1.0 Org1 mychannel GO_LANG aef123d2s....";

    public static InstallChaincodeRequest parseArgs(java.lang.Object[] args) throws Exception {
        check(args);

        String chaincodeName = (String) args[0];
        String version = (String) args[1];
        String orgName = (String) args[2];
        String channelName = (String) args[3];
        String language = (String) args[4];
        byte[] code = (byte[]) args[5];

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

    private static void check(java.lang.Object[] args) throws Exception {
        if (args.length != 6) {
            throw new Exception("Args length is not 6 but " + args.length + "\n" + DESCRIPTION);
        }
    }
}
