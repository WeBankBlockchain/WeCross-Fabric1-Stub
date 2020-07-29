package com.webank.wecross.stub.fabric.FabricCustomCommand;

import java.util.Base64;

public class InstallCommand {

    public static final String NAME = "install";
    public static final String DESCRIPTION =
            "Command:\tinstall [chaincodeName] [version] [orgName] [language(GO_LANG/JAVA)] [code(generate with generateTarGzInputStreamBytes() function)]\n"
                    + "Return:\tString(Success or Failed)\n"
                    + "Eg:\n"
                    + "       \tinstall sacc 1.0 Org1 GO_LANG aef123d2s....";

    // parse args from sdk
    public static InstallChaincodeRequest parseEncodedArgs(
            java.lang.Object[] encodedArgs, String channelName) throws Exception {

        if (encodedArgs.length != 5) {
            throw new Exception(
                    "InstallChaincodeRequest args length is not 5 but: " + encodedArgs.length);
        }

        Object[] args = {
            encodedArgs[0], // chaincodeName
            encodedArgs[1], // version
            encodedArgs[2], // orgName
            encodedArgs[3], // language
            Base64.getDecoder().decode((String) encodedArgs[4]) // code
        };

        return parseArgs(args, channelName);
    }

    // parse args inside stub
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
