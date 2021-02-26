package com.webank.wecross.utils;

import com.moandjiezana.toml.Toml;
import com.webank.wecross.exception.WeCrossException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.hyperledger.fabric.sdk.ChaincodeEndorsementPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public class FabricUtils {

    public static final String CERT_PATTERN =
            "-+BEGIN\\s+.*CERTIFICATE[^-]*-+(?:\\s|\\r|\\n)+"
                    + // Header
                    "([A-Za-z0-9+/=\\r\\n]+)"
                    + // Base64 text
                    "-+END\\s+.*CERTIFICATE[^-]*-+\n"; // Footer

    private static final Map<String, String> fileContentMap = new HashMap<>();

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

    public static String readFileContent(String fileName) throws Exception {
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
            return content;
        } catch (Exception e) {
            throw new Exception("Read file error: " + e);
        }
    }

    public static Map<String, String> readFileInMap(Map<String, String> map)
            throws WeCrossException {
        if (map == null) return null;
        // map: key => filePath
        // resultMap: key => fileContent
        Map<String, String> resultMap = new HashMap<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (Pattern.compile(FabricUtils.CERT_PATTERN, Pattern.MULTILINE)
                    .matcher(entry.getValue())
                    .matches()) {
                resultMap.put(entry.getKey(), entry.getValue());
                continue;
            }
            if (!fileIsExists(entry.getValue())) {
                String errorMessage = "File: " + entry.getValue() + " is not exists";
                throw new WeCrossException(WeCrossException.ErrorCode.DIR_NOT_EXISTS, errorMessage);
            }
            String fileContent;
            try {
                // fileContentMap cache file content
                if (fileContentMap.containsKey(entry.getValue())
                        && fileContentMap.get(entry.getValue()) != null) {
                    fileContent = fileContentMap.get(entry.getValue());
                } else {
                    fileContent = readFileContent(entry.getValue());
                    fileContentMap.put(entry.getValue(), fileContent);
                }
                resultMap.put(entry.getKey(), fileContent);
            } catch (Exception e) {
                throw new WeCrossException(
                        WeCrossException.ErrorCode.DIR_NOT_EXISTS,
                        "Read Cert fail: " + entry.getKey() + entry.getValue());
            }
        }
        return resultMap;
    }

    public static Toml readToml(String fileName) throws Exception {
        return new Toml().read(readFileContent(fileName));
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

    public static String readFileToBytesString(String filePath) throws Exception {
        String content = readFileContent(filePath);
        return Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
    }

    public static String readPolicyYamlFileToBytesString(String filePath) throws Exception {
        return readFileToBytesString(filePath);
    }

    public static ChaincodeEndorsementPolicy parsePolicyBytesString(String bytesString)
            throws Exception {
        ChaincodeEndorsementPolicy chaincodeEndorsementPolicy = new ChaincodeEndorsementPolicy();
        if (bytesString == null || bytesString.length() == 0) {
            chaincodeEndorsementPolicy.fromBytes(new byte[] {});
        } else {
            byte[] bytes = Base64.getDecoder().decode(bytesString);
            String content = new String(bytes, StandardCharsets.UTF_8);
            File tmpFile = File.createTempFile("policy-" + System.currentTimeMillis(), ".tmp");
            try {
                FileWriter writer = new FileWriter(tmpFile);
                writer.write(content);
                writer.close();

                InputStream targetStream = new ByteArrayInputStream(bytes);
                chaincodeEndorsementPolicy.fromYamlFile(tmpFile);
            } finally {
                tmpFile.delete();
            }
        }

        return chaincodeEndorsementPolicy;
    }
}
