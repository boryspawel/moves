package com.motionecosystem.exercisecatalog;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.motionecosystem.audit.AuditRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final ExerciseRepository exercises;
    private final ExerciseVersionRepository versions;
    private final AuditRecorder audit;
    private final Clock clock;

    @Transactional
    public VersionView create(String actorSubject, String canonicalName, VersionCommand requested) {
        String name = requiredText(canonicalName, 160, "canonical name");
        VersionCommand command = validate(requested);
        Instant now = clock.instant();
        Exercise exercise = exercises.save(new Exercise(name, actorSubject, now));
        ExerciseVersion version = versions.save(new ExerciseVersion(exercise.id, 1, command, now));
        audit.record(actorSubject, "EXERCISE_CREATED", "Exercise", exercise.id);
        return view(exercise, version);
    }

    @Transactional
    public VersionView createNextVersion(String actorSubject, UUID exerciseId, VersionCommand requested) {
        Exercise exercise = exercise(exerciseId);
        int number = versions.findFirstByExerciseIdOrderByVersionNumberDesc(exerciseId)
                .map(item -> item.versionNumber + 1)
                .orElse(1);
        ExerciseVersion version = versions.save(new ExerciseVersion(exerciseId, number, validate(requested), clock.instant()));
        audit.record(actorSubject, "EXERCISE_VERSION_CREATED", "ExerciseVersion", version.id);
        return view(exercise, version);
    }

    @Transactional
    public VersionView updateDraft(String actorSubject, UUID versionId, VersionCommand requested) {
        ExerciseVersion version = version(versionId);
        try {
            version.update(validate(requested));
        } catch (IllegalStateException immutable) {
            throw conflict(immutable.getMessage());
        }
        audit.record(actorSubject, "EXERCISE_DRAFT_UPDATED", "ExerciseVersion", version.id);
        return view(exercise(version.exerciseId), version);
    }

    @Transactional
    public VersionView publish(String actorSubject, UUID versionId) {
        ExerciseVersion version = version(versionId);
        try {
            version.publish(clock.instant());
        } catch (IllegalStateException invalidState) {
            throw conflict(invalidState.getMessage());
        }
        audit.record(actorSubject, "EXERCISE_VERSION_PUBLISHED", "ExerciseVersion", version.id);
        return view(exercise(version.exerciseId), version);
    }

    @Transactional
    public VersionView withdraw(String actorSubject, UUID versionId) {
        ExerciseVersion version = version(versionId);
        try {
            version.withdraw(clock.instant());
        } catch (IllegalStateException invalidState) {
            throw conflict(invalidState.getMessage());
        }
        audit.record(actorSubject, "EXERCISE_VERSION_WITHDRAWN", "ExerciseVersion", version.id);
        return view(exercise(version.exerciseId), version);
    }

    @Transactional(readOnly = true)
    public List<VersionView> listPublished(String query,
                                           MovementPattern movementPattern,
                                           TechnicalLevel technicalLevel,
                                           String equipment,
                                           String excludedContraindicationTag) {
        String normalizedQuery = normalize(query);
        String normalizedEquipment = normalizeTag(equipment);
        String excludedTag = normalizeTag(excludedContraindicationTag);
        return versions.findByStatus(ExerciseVersionStatus.PUBLISHED).stream()
                .map(version -> view(exercise(version.exerciseId), version))
                .filter(item -> normalizedQuery == null
                        || item.canonicalName().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || item.instruction().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .filter(item -> movementPattern == null || item.movementPattern() == movementPattern)
                .filter(item -> technicalLevel == null || item.technicalLevel() == technicalLevel)
                .filter(item -> normalizedEquipment == null || item.requiredEquipment().contains(normalizedEquipment))
                .filter(item -> excludedTag == null || !item.contraindicationTags().contains(excludedTag))
                .toList();
    }

    @Transactional(readOnly = true)
    public VersionView published(UUID versionId) {
        ExerciseVersion version = version(versionId);
        if (version.status != ExerciseVersionStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "exercise version not found");
        }
        return view(exercise(version.exerciseId), version);
    }

    @Transactional(readOnly = true)
    public List<VersionView> allVersions(UUID exerciseId) {
        Exercise exercise = exercise(exerciseId);
        return versions.findByExerciseIdOrderByVersionNumber(exerciseId).stream()
                .map(version -> view(exercise, version))
                .toList();
    }

    private Exercise exercise(UUID id) {
        return exercises.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "exercise not found"));
    }

    private ExerciseVersion version(UUID id) {
        return versions.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "exercise version not found"));
    }

    private static VersionCommand validate(VersionCommand command) {
        if (command == null || command.movementPattern == null || command.stimulusType == null
                || command.fatigueProfile == null || command.technicalLevel == null || command.environment == null) {
            throw badRequest("all classification fields are required");
        }
        return new VersionCommand(
                requiredText(command.instruction, 10_000, "instruction"),
                optionalText(command.mediaReference, 2_000, "media reference"),
                command.movementPattern,
                command.stimulusType,
                command.fatigueProfile,
                command.technicalLevel,
                command.environment,
                normalizedTags(command.requiredEquipment),
                normalizedTags(command.contraindicationTags));
    }

    private static Set<String> normalizedTags(Collection<String> values) {
        if (values == null) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        values.forEach(value -> {
            String tag = normalizeTag(value);
            if (tag == null || tag.length() > 80) {
                throw badRequest("equipment and contraindication tags must contain 1-80 characters");
            }
            normalized.add(tag);
        });
        return Set.copyOf(normalized);
    }

    private static String requiredText(String value, int max, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw badRequest(field + " is required and too long values are rejected");
        }
        return normalized;
    }

    private static String optionalText(String value, int max, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requiredText(value, max, field);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeTag(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static VersionView view(Exercise exercise, ExerciseVersion version) {
        return new VersionView(
                exercise.id,
                exercise.canonicalName,
                version.id,
                version.versionNumber,
                version.status,
                version.instruction,
                version.mediaReference,
                version.movementPattern,
                version.stimulusType,
                version.fatigueProfile,
                version.technicalLevel,
                version.environment,
                Set.copyOf(version.requiredEquipment),
                Set.copyOf(version.contraindicationTags),
                version.publishedAt,
                version.withdrawnAt);
    }

    public record VersionCommand(
            String instruction,
            String mediaReference,
            MovementPattern movementPattern,
            StimulusType stimulusType,
            FatigueProfile fatigueProfile,
            TechnicalLevel technicalLevel,
            ExerciseEnvironment environment,
            Set<String> requiredEquipment,
            Set<String> contraindicationTags) {
    }

    public record VersionView(
            UUID exerciseId,
            String canonicalName,
            UUID versionId,
            int versionNumber,
            ExerciseVersionStatus status,
            String instruction,
            String mediaReference,
            MovementPattern movementPattern,
            StimulusType stimulusType,
            FatigueProfile fatigueProfile,
            TechnicalLevel technicalLevel,
            ExerciseEnvironment environment,
            Set<String> requiredEquipment,
            Set<String> contraindicationTags,
            Instant publishedAt,
            Instant withdrawnAt) {
    }
}
