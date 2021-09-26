package com.webank.wecross.stub.fabric.FabricCustomCommand;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.wecross.stub.ObjectMapperFactory;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstallAndInstantiateCommand {
    public static final String NAME = "install-instantiate";
    private static Logger logger = LoggerFactory.getLogger(InstallAndInstantiateCommand.class);
    /*
    Args definition:
       String chaincodeName = (String) args[0];
       String version = (String) args[1];
       String language = (String) args[2];
       byte[] code = (byte[]) args[3];
       String endorsementPolicy = (String) args[4];
       String[] instantiateArgs = (String[]) args[5];
    */

    public static List<Object> toInstallArgs(List<Object> encodedArgs, String orgName) {
        /*
        // install args definition:
        String chaincodeName = (String) args[0];
        String version = (String) args[1];
        String orgName = (String) args[2];
        String language = (String) args[3];
        byte[] code = (byte[]) args[4];
        * */

        Object[] args = encodedArgs.toArray(new Object[0]);

        List<Object> ret = new ArrayList<>();
        ret.add(args[0]); //  chaincodeName
        ret.add(args[1]); // version
        ret.add(orgName); // orgName
        ret.add(args[2]); // language
        ret.add(args[3]); // code
        return ret;
    }

    public static List<Object> toInstantiateArgs(List<Object> encodedArgs, List<String> orgNames) {
        /*
        // instantiate args definition:
        String chaincodeName = (String) args[0];
        String version = (String) args[1];
        String[] orgNames = (String[]) args[2];
        String language = (String) args[3];
        String endorsementPolicy = (String) args[4];
        String[] instantiateArgs = (String[]) args[5];
        * */
        ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();
        String orgNamesStr = null;
        try {
            orgNamesStr = objectMapper.writeValueAsString(orgNames);
            System.out.println("To orgs: " + orgNamesStr);
        } catch (Exception e) {
            logger.error("Could not write orgs as string"); // must not goes here
        }

        Object[] args = encodedArgs.toArray(new Object[0]);

        List<Object> ret = new ArrayList<>();
        ret.add(args[0]); //  chaincodeName
        ret.add(args[1]); // version
        ret.add(orgNamesStr); // orgNames
        ret.add(args[2]); // language
        ret.add(args[4]); // endorsementPolicy
        ret.add(args[5]); // instantiateArgs
        return ret;
    }
}
