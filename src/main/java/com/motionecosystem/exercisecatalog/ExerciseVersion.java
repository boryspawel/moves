package com.motionecosystem.exercisecatalog;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "exercise_version", schema = "exercise_catalog")
class ExerciseVersion {

    @Id
    UUID id;
    @Column(name = "exercise_id", nullable = false)
    UUID exerciseId;
    @Column(name = "version_number", nullable = false)
    int versionNumber;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ExerciseVersionStatus status;
    @Column(nullable = false, columnDefinition = "text")
    String instruction;
    @Column(name = "media_reference", columnDefinition = "text")
    String mediaReference;
    @Enumerated(EnumType.STRING)
    @Column(name = "movement_pattern", nullable = false)
    MovementPattern movementPattern;
    @Enumerated(EnumType.STRING)
    @Column(name = "stimulus_type", nullable = false)
    StimulusType stimulusType;
    @Enumerated(EnumType.STRING)
    @Column(name = "fatigue_profile", nullable = false)
    FatigueProfile fatigueProfile;
    @Enumerated(EnumType.STRING)
    @Column(name = "technical_level", nullable = false)
    TechnicalLevel technicalLevel;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ExerciseEnvironment environment;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "exercise_version_equipment", schema = "exercise_catalog",
            joinColumns = @JoinColumn(name = "exercise_version_id"))
    @Column(name = "equipment")
    Set<String> requiredEquipment = new LinkedHashSet<>();
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "exercise_version_contraindication", schema = "exercise_catalog",
            joinColumns = @JoinColumn(name = "exercise_version_id"))
    @Column(name = "contraindication_tag")
    Set<String> contraindicationTags = new LinkedHashSet<>();
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;
    @Column(name = "published_at")
    Instant publishedAt;
    @Column(name = "withdrawn_at")
    Instant withdrawnAt;
    @Version
    long version;

    protected ExerciseVersion() {
    }

    ExerciseVersion(UUID exerciseId, int versionNumber, CatalogService.VersionCommand command, Instant now) {
        id = UUID.randomUUID();
        this.exerciseId = exerciseId;
        this.versionNumber = versionNumber;
        status = ExerciseVersionStatus.DRAFT;
        createdAt = now;
        apply(command);
    }

    void update(CatalogService.VersionCommand command) {
        requireDraft();
        apply(command);
    }

    void publish(Instant now) {
        requireDraft();
        status = ExerciseVersionStatus.PUBLISHED;
        publishedAt = now;
    }

    void withdraw(Instant now) {
        if (status != ExerciseVersionStatus.PUBLISHED) {
            throw new IllegalStateException("only a published version can be withdrawn");
        }
        status = ExerciseVersionStatus.WITHDRAWN;
        withdrawnAt = now;
    }

    private void apply(CatalogService.VersionCommand command) {
        instruction = command.instruction();
        mediaReference = command.mediaReference();
        movementPattern = command.movementPattern();
        stimulusType = command.stimulusType();
        fatigueProfile = command.fatigueProfile();
        technicalLevel = command.technicalLevel();
        environment = command.environment();
        requiredEquipment = new LinkedHashSet<>(command.requiredEquipment());
        contraindicationTags = new LinkedHashSet<>(command.contraindicationTags());
    }

    private void requireDraft() {
        if (status != ExerciseVersionStatus.DRAFT) {
            throw new IllegalStateException("published or withdrawn versions are immutable");
        }
    }
}
