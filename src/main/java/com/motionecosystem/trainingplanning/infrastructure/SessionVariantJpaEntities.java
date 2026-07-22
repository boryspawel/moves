package com.motionecosystem.trainingplanning.infrastructure;

import com.motionecosystem.trainingplanning.TrainingPlanningModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity(name = "PlannedSessionVariantJpaEntity")
@Table(name = "planned_session_variant", schema = "training_planning")
class PlannedSessionVariantJpaEntity {
    @Id UUID id;
    @Column(name = "planned_session_id", nullable = false) UUID plannedSessionId;
    @Column(name = "variant_type", nullable = false) String variantType;
    @Column(name = "expected_duration_minutes") Integer expectedDurationMinutes;
    protected PlannedSessionVariantJpaEntity() { }
    PlannedSessionVariantJpaEntity(TrainingPlanningModel.SessionVariant source) {
        id = source.id(); plannedSessionId = source.plannedSessionId();
        variantType = source.type().name(); expectedDurationMinutes = source.expectedDurationMinutes();
    }
}

@Entity(name = "PlannedSessionVariantItemJpaEntity")
@Table(name = "planned_session_variant_item", schema = "training_planning")
class PlannedSessionVariantItemJpaEntity {
    @Id UUID id;
    @Column(name = "session_variant_id", nullable = false) UUID sessionVariantId;
    @Column(name = "base_prescription_id", nullable = false) UUID basePrescriptionId;
    @Column(nullable = false) int position;
    @Column(name = "override_sets") Integer overrideSets;
    @Column(name = "override_repetitions") Integer overrideRepetitions;
    @Column(name = "override_duration_seconds") Integer overrideDurationSeconds;
    @Column(name = "override_contacts") Integer overrideContacts;
    protected PlannedSessionVariantItemJpaEntity() { }
    PlannedSessionVariantItemJpaEntity(TrainingPlanningModel.SessionVariantItem source) {
        id = source.id(); sessionVariantId = source.sessionVariantId(); basePrescriptionId = source.basePrescriptionId();
        position = source.position(); overrideSets = source.overrideSets(); overrideRepetitions = source.overrideRepetitions();
        overrideDurationSeconds = source.overrideDurationSeconds(); overrideContacts = source.overrideContacts();
    }
}
