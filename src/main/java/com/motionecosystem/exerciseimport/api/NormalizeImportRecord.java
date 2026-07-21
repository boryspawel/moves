package com.motionecosystem.exerciseimport.api;

import java.util.UUID;

/** Deterministic, versioned normalization independent of Spring Batch. */
public interface NormalizeImportRecord {
    void normalize(UUID recordId);
}
