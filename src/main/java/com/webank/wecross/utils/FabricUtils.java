package com.webank.wecross.utils;

import com.moandjiezana.toml.Toml;
import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;
import org.hyperledger.fabric.sdk.ChaincodeEndorsementPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public class FabricUtils {

    public static byte[] longToBytes(long number) {
        BigInteger bigInteger = BigInteger.valueOf(number);

        return bigInteger.toByteArray();
    }

    public static long bytesToLong(byte[] bytes) {
        BigInteger bigInteger = new BigInteger(bytes);
        return bigInteger.longValue();
    }

    public static String getPath(String fileName) throws Exception {
        try {
            if (fileName.indexOf("classpath:") != 0) {
                return fileName;
            }
            PathMatchingResourcePatternResolver resolver =
                    new PathMatchingResourcePatternResolver();
            Path path;
            path = Paths.get(resolver.getResource(fileName).getURI());

            Logger logger = LoggerFactory.getLogger(FabricUtils.class);
            logger.debug("relative path:{} absolute path:{}", fileName, path.toString());
            return path.toString();
        } catch (Exception e) {
            throw new Exception("getPath exception: " + e);
        }
    }

    public static File getFile(String fileName) throws Exception {
        try {
            File file;
            if (fileName.indexOf("classpath:") != 0) {
                file = new File(fileName);
            } else {
                PathMatchingResourcePatternResolver resolver =
                        new PathMatchingResourcePatternResolver();
                file = resolver.getResource(fileName).getFile();
            }
            Logger logger = LoggerFactory.getLogger(FabricUtils.class);
            logger.debug("relative path:{} absolute path:{}", fileName, file.getAbsolutePath());
            return file;
        } catch (Exception e) {
            throw new Exception("getFile exception: " + e);
        }
    }

    public static Toml readToml(String fileName) throws Exception {
        try {
            Path path;

            if (fileName.indexOf("classpath:") != 0) {
                path = Paths.get(fileName);
            } else {
                // Start with "classpath:"
                PathMatchingResourcePatternResolver resolver =
                        new PathMatchingResourcePatternResolver();
                path = Paths.get(resolver.getResource(fileName).getURI());
            }

            String content = new String(Files.readAllBytes(path));
            return new Toml().read(content);
        } catch (Exception e) {
            throw new Exception("Read toml file error: " + e);
        }
    }

    public static Map<String, Object> readTomlMap(String fileName) throws Exception {
        return readToml(fileName).toMap();
    }

    // Check if the file exists or not
    public static boolean fileIsExists(String path) {
        try {
            PathMatchingResourcePatternResolver resolver_temp =
                    new PathMatchingResourcePatternResolver();
            resolver_temp.getResource(path).getFile();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public static String readPolicyYamlFileToBytesString(String filePath) throws Exception {
        File yamlFile = getFile(filePath);
        ChaincodeEndorsementPolicy endorsementPolicy = new ChaincodeEndorsementPolicy();
        endorsementPolicy.fromYamlFile(yamlFile);
        byte[] policyBytes = endorsementPolicy.getChaincodeEndorsementPolicyAsBytes();
        return Base64.getEncoder().encodeToString(policyBytes);
    }

    public static ChaincodeEndorsementPolicy parsePolicyBytesString(String bytesString) {
        byte[] bytes = Base64.getDecoder().decode(bytesString);
        ChaincodeEndorsementPolicy chaincodeEndorsementPolicy = new ChaincodeEndorsementPolicy();
        chaincodeEndorsementPolicy.fromBytes(bytes);
        return chaincodeEndorsementPolicy;
    }
}
