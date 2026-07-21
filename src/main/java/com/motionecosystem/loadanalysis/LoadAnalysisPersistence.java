package com.motionecosystem.loadanalysis;

import java.util.Optional;
import java.util.UUID;

import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort.LoadProfile;

public interface LoadAnalysisPersistence {
    Optional<LoadProfile> find(UUID revisionId, String checksum, String algorithmVersion,
                               String configurationVersion);
    LoadProfile save(LoadProfile profile);
}
