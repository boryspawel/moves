package com.motionecosystem.exerciseimport;

import com.motionecosystem.exerciseimport.api.ImportArtifactStorage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class FileSystemImportArtifactStorage implements ImportArtifactStorage {
    private final Path root;

    FileSystemImportArtifactStorage(@Value("${exercise-import.artifacts.directory}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public StoredArtifact store(UUID artifactId, String originalFilename, InputStream input, long maximumBytes)
            throws IOException {
        Files.createDirectories(root);
        String storageKey = artifactId + ".jsonl";
        Path target = safePath(storageKey);
        MessageDigest digest = sha256();
        long count = 0;
        boolean complete = false;
        try (InputStream source = new BufferedInputStream(input);
             OutputStream output = new BufferedOutputStream(Files.newOutputStream(target,
                     StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = source.read(buffer)) >= 0) {
                if (read == 0) continue;
                count += read;
                if (count > maximumBytes) {
                    throw new ArtifactTooLargeException(maximumBytes);
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
            complete = true;
        } finally {
            if (!complete) Files.deleteIfExists(target);
        }
        return new StoredArtifact(storageKey, count, HexFormat.of().formatHex(digest.digest()));
    }

    @Override
    public InputStream open(String storageKey) throws IOException {
        return new BufferedInputStream(Files.newInputStream(safePath(storageKey), StandardOpenOption.READ));
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(safePath(storageKey));
    }

    @Override
    public Path root() {
        return root;
    }

    private Path safePath(String storageKey) {
        Path path = root.resolve(storageKey).normalize();
        if (!path.getParent().equals(root)) throw new IllegalArgumentException("invalid artifact storage key");
        return path;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static final class ArtifactTooLargeException extends IOException {
        ArtifactTooLargeException(long limit) {
            super("artifact exceeds configured limit of " + limit + " bytes");
        }
    }
}
