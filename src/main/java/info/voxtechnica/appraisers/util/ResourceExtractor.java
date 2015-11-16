package info.voxtechnica.appraisers.util;

import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import javax.validation.constraints.NotNull;
import java.io.*;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Enumeration;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Extract Resource(s) from the Jar file and put them in the specified location in the local file system
 */
public class ResourceExtractor {

    public static void extractResource(@NotNull String resource, @NotNull String destination) throws IOException {
        extractResource(resource, destination, false);
    }

    public static void extractResource(@NotNull String resource, @NotNull String destination, @NotNull Boolean executable) throws IOException {
        // Check to see if the destination already exists (don't bother overwriting)
        Path destinationFile = Paths.get(destination);
        if (Files.notExists(destinationFile)) {
            try (OutputStream out = new FileOutputStream(destinationFile.toFile())) {
                URL sourceResource = Resources.getResource(resource);
                Resources.copy(sourceResource, out);
                if (executable) {
                    Set<PosixFilePermission> permissions = Sets.newHashSet(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE,
                            PosixFilePermission.GROUP_READ,
                            PosixFilePermission.GROUP_EXECUTE,
                            PosixFilePermission.OTHERS_READ,
                            PosixFilePermission.OTHERS_EXECUTE);
                    Files.setPosixFilePermissions(destinationFile, permissions);
                }
            }
        }
    }

    public static void extractResources(@NotNull String source, @NotNull String destination) throws IOException {
        // Check to see if the destination already exists (don't bother overwriting)
        File destPath = new File(destination);
        if (destPath.exists()) return;
        URL resourceUrl = Resources.getResource(source);
        URLConnection urlConnection = resourceUrl.openConnection();
        if (urlConnection instanceof JarURLConnection) {
            // Extract resources from a jar file
            JarURLConnection jarConnection = (JarURLConnection) urlConnection;
            JarFile jarFile = jarConnection.getJarFile();
            String jarConnectionEntryName = jarConnection.getEntryName();
            // Iterate all entries in the jar file
            for (Enumeration<JarEntry> e = jarFile.entries(); e.hasMoreElements(); ) {
                JarEntry jarEntry = e.nextElement();
                String jarEntryName = jarEntry.getName();
                // Extract files only if they match the path
                if (jarEntryName.startsWith(jarConnectionEntryName)) {
                    String filename = jarEntryName.substring(jarConnectionEntryName.length());
                    File currentFile = new File(destPath, filename);
                    if (jarEntry.isDirectory())
                        currentFile.mkdirs();
                    else try (
                            InputStream inputStream = jarFile.getInputStream(jarEntry);
                            OutputStream outputStream = FileUtils.openOutputStream(currentFile)) {
                        IOUtils.copy(inputStream, outputStream);
                    }
                }
            }
        } else {
            // Extract resources from the file system (i.e. in development; there's no jar file)
            File file = new File(resourceUrl.getPath());
            if (file.isDirectory()) {
                FileUtils.copyDirectory(file, destPath);
            } else {
                FileUtils.copyFile(file, destPath);
            }
        }
    }

}
