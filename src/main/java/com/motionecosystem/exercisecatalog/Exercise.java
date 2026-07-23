package com.motionecosystem.exercisecatalog;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "exercise", schema = "exercise_catalog")
class Exercise {

    @Id
    UUID id;
    @Column(name = "canonical_name", nullable = false)
    String canonicalName;
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;
    @Column(name = "created_by_subject", nullable = false, updatable = false)
    String createdBySubject;

    protected Exercise() {
    }

    Exercise(String canonicalName, String createdBySubject, Instant now) {
        id = UUID.randomUUID();
        this.canonicalName = canonicalName;
        this.createdBySubject = createdBySubject;
        createdAt = now;
    }

    void rename(String canonicalName) {
        this.canonicalName = canonicalName;
    }
}
