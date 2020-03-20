package com.webank.wecross.utils;

import com.moandjiezana.toml.Toml;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public class FabricUtils {
    private static Logger logger = LoggerFactory.getLogger(FabricUtils.class);

    public static byte[] longToBytes(long number) {
        return ByteBuffer.allocate(Long.BYTES).putLong(number).array();
    }

    public static long bytesToLong(byte[] bytes) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(Long.BYTES).put(bytes);
        byteBuffer.flip();
        return byteBuffer.getLong();
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
            logger.debug("relative path:{} absolute path:{}", fileName, path.toString());
            return path.toString();
        } catch (Exception e) {
            throw new Exception(e.getMessage());
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
            throw new Exception("Read toml file error: " + e.getMessage());
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
