package com.webank.wecross.utils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public class TarUtils {

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
        pathPrefix =
                "src/" + pathPrefix; // Huuugggeee hole here!!! the path in tar.gz must start with
        // src/ and then with pathPrefix (sad)
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
            FileInputStream fileInputStream;
            for (File childFile : childrenFiles) {
                String childPath = childFile.getAbsolutePath();
                String relativePath =
                        childPath.substring((sourcePath.length() + 1), childPath.length());

                if (pathPrefix != null) {
                    relativePath = Paths.get(pathPrefix, relativePath).toString();
                }

                relativePath = FilenameUtils.separatorsToUnix(relativePath);

                archiveEntry = new TarArchiveEntry(childFile, relativePath);
                fileInputStream = new FileInputStream(childFile);
                archiveOutputStream.putArchiveEntry(archiveEntry);

                try {
                    IOUtils.copy(fileInputStream, archiveOutputStream);
                } finally {
                    IOUtils.closeQuietly(fileInputStream);
                    archiveOutputStream.closeArchiveEntry();
                }
            }
        } finally {
            IOUtils.closeQuietly(archiveOutputStream);
        }

        return bos.toByteArray();
    }

    private static InputStream generateTarGzInputStream(File src, String pathPrefix)
            throws IOException {
        return new ByteArrayInputStream(generateTarGzInputStreamBytes(src, pathPrefix));
    }

    public static byte[] generateTarGzInputStreamBytes(File src) throws IOException {
        return generateTarGzInputStreamBytes(
                src, "chaincode"); // Inside tar.gz is: src/chaincode/<where chaincode_main.go is>
    }

    public static InputStream generateTarGzInputStream(File src) throws IOException {
        return generateTarGzInputStream(
                src, "chaincode"); // Inside tar.gz is: src/chaincode/<where chaincode_main.go is>
    }

    public static byte[] generateTarGzInputStreamBytes(String path) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Path file = Paths.get(resolver.getResource(path).getURI());
        return generateTarGzInputStreamBytes(
                file.toFile()); // Inside tar.gz is: src/chaincode/<where chaincode_main.go is>
    }
}
