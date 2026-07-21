package com.motionecosystem.exerciseimport.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;

public interface ImportArtifactStorage {
    StoredArtifact store(UUID artifactId, String originalFilename, InputStream input, long maximumBytes)
            throws IOException;
    InputStream open(String storageKey) throws IOException;
    void delete(String storageKey) throws IOException;
    Path root();

    record StoredArtifact(String storageKey, long byteSize, String sha256) {
    }
}
