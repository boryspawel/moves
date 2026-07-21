package com.motionecosystem.loadanalysis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.motionecosystem.anatomyreference.api.AnatomyReferenceQueryPort;
import com.motionecosystem.exercisecatalog.api.ExerciseCatalogQueryPort;
import com.motionecosystem.exercisecatalog.api.ExerciseCatalogQueryPort.PublishedExerciseVersionSnapshot;
import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort;
import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort.LoadCalculationVersion;
import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort.LoadProfile;
import com.motionecosystem.loadanalysis.domain.LoadCalculator;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort.PlanRevisionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlannedLoadService implements PlannedLoadCalculationPort {
    private final ExerciseCatalogQueryPort catalog;
    private final AnatomyReferenceQueryPort anatomy;
    private final LoadAnalysisPersistence persistence;
    private final Clock clock;
    private final LoadCalculator calculator = new LoadCalculator();

    @Override
    @Transactional
    public LoadProfile calculate(PlanRevisionSnapshot plan, LoadCalculationVersion version) {
        if (plan == null || version == null || blank(version.algorithmVersion())
                || blank(version.configurationVersion())) {
            throw new IllegalArgumentException("revision and calculation version are required");
        }
        Set<UUID> exerciseIds = new LinkedHashSet<>();
        plan.cycles().forEach(cycle -> cycle.microcycles().forEach(micro -> micro.sessions().forEach(session ->
                session.prescriptions().forEach(item -> exerciseIds.add(item.exerciseVersionId())))));
        Map<UUID, PublishedExerciseVersionSnapshot> profiles = catalog.findPublishedVersions(exerciseIds);
        if (profiles.size() != exerciseIds.size()) {
            throw new IllegalArgumentException("all prescriptions require published catalog profiles");
        }
        String checksum = checksum(canonical(plan, profiles, version));
        var existing = persistence.find(plan.revisionId(), checksum,
                version.algorithmVersion(), version.configurationVersion());
        if (existing.isPresent()) return existing.get();
        Set<UUID> structures = profiles.values().stream().flatMap(item -> item.contributions().stream())
                .filter(item -> item.calculationRole()
                        == ExerciseCatalogQueryPort.CalculationRoleValue.ALLOCATION)
                .map(item -> item.anatomicalStructureId()).collect(java.util.stream.Collectors.toSet());
        Map<UUID, Set<UUID>> ancestors = structures.stream().collect(java.util.stream.Collectors.toMap(
                id -> id, id -> anatomy.ancestorPaths(id).stream().flatMap(path -> path.steps().stream())
                        .map(step -> step.structure().id()).collect(java.util.stream.Collectors.toSet())));
        LoadProfile calculated = calculator.calculate(plan, profiles, ancestors, version,
                UUID.randomUUID(), checksum, clock.instant());
        return persistence.save(calculated);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static String canonical(PlanRevisionSnapshot plan,
                                    Map<UUID, PublishedExerciseVersionSnapshot> profiles,
                                    LoadCalculationVersion version) {
        StringBuilder value = new StringBuilder(plan.toString()).append('|')
                .append(version.algorithmVersion()).append('|').append(version.configurationVersion());
        profiles.values().stream().sorted(java.util.Comparator.comparing(item -> item.versionId().toString()))
                .forEach(profile -> {
                    value.append('|').append(profile.versionId()).append(':').append(profile.versionNumber())
                            .append(':').append(profile.profileSchemaVersion());
                    profile.contributions().stream()
                            .sorted(java.util.Comparator.comparing(item -> item.id().toString()))
                            .forEach(contribution -> value.append('|').append(contribution));
                });
        return value.toString();
    }

    private static String checksum(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
