package com.webank.wecross.utils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Collection;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public class TarUtils {

    // Huuugggeee hole here!!! the path in tar.gz must start with src/ and then with pathPrefix
    public static final String goPrefix = "src/chaincode";

    /**
     * Generate a targz inputstream from source folder.
     *
     * @param src Source location
     * @param pathPrefix prefix to add to the all files found.
     * @return return inputstream.
     * @throws IOException
     */
    private static byte[] generateTarGzInputStreamBytes(File src, String pathPrefix)
            throws IOException {

        File sourceDirectory = src;

        ByteArrayOutputStream bos = new ByteArrayOutputStream(500000);

        String sourcePath = sourceDirectory.getAbsolutePath();

        TarArchiveOutputStream archiveOutputStream =
                new TarArchiveOutputStream(
                        new GzipCompressorOutputStream(new BufferedOutputStream(bos)));
        archiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

        try {
            Collection<File> childrenFiles =
                    org.apache.commons.io.FileUtils.listFiles(sourceDirectory, null, true);

            ArchiveEntry archiveEntry;
            for (File childFile : childrenFiles) {
                String childPath = childFile.getAbsolutePath();
                String relativePath =
                        childPath.substring((sourcePath.length() + 1), childPath.length());

                if (pathPrefix != null) {
                    relativePath = Paths.get(pathPrefix, relativePath).toString();
                }

                relativePath = FilenameUtils.separatorsToUnix(relativePath);

                archiveEntry = new TarArchiveEntry(childFile, relativePath);
                try (FileInputStream fileInputStream = new FileInputStream(childFile)) {
                    archiveOutputStream.putArchiveEntry(archiveEntry);

                    try {
                        IOUtils.copy(fileInputStream, archiveOutputStream);
                    } finally {
                        archiveOutputStream.closeArchiveEntry();
                    }
                }
            }
        } finally {
            IOUtils.closeQuietly(archiveOutputStream);
        }

        return bos.toByteArray();
    }

    public static byte[] generateTarGzInputStreamBytes(String path, String pathPrefix)
            throws IOException {
        System.out.println("path: " + path);
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        File file = resolver.getResource(path).getFile();
        return generateTarGzInputStreamBytes(
                file, pathPrefix); // Inside tar.gz is: src/chaincode/<where chaincode_main.go is>
    }

    public static byte[] generateTarGzInputStreamBytesFoGoChaincode(String path)
            throws IOException {
        System.out.println("path: " + path);
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        File file = resolver.getResource(path).getFile();
        return generateTarGzInputStreamBytes(
                file, goPrefix); // Inside tar.gz is: src/chaincode/<where chaincode_main.go is>
    }

    public static String generateTarGzInputStreamEncodedString(String path) throws IOException {
        return Base64.getEncoder().encodeToString(generateTarGzInputStreamBytes(path, ""));
    }

    public static String generateTarGzInputStreamEncodedStringFoGoChaincode(String path)
            throws IOException {

        return Base64.getEncoder().encodeToString(generateTarGzInputStreamBytes(path, goPrefix));
    }
}
