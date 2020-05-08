package com.webank.wecross.utils;

import com.moandjiezana.toml.Toml;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
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
}
