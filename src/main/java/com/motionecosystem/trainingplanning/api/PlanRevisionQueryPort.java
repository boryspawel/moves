package com.motionecosystem.trainingplanning.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Immutable revision boundary for load analysis and execution orchestration. */
public interface PlanRevisionQueryPort {

    Optional<PlanRevisionSnapshot> findRevision(UUID revisionId);

    Optional<PlanRevisionSnapshot> findActiveRevision(UUID participantAccountId);

    record PlanRevisionSnapshot(
            UUID revisionId, UUID planId, UUID participantAccountId, int revisionNumber,
            UUID basedOnRevisionId, long revisionVersion, String status,
            UUID authorAccountId, String authorCapability, Instant createdAt,
            String migrationOrigin, String assessmentStatus,
            String phaseIntent, LocalDate validFrom, LocalDate validTo,
            List<GoalSnapshot> goals, List<CycleSnapshot> cycles, List<LoadBudgetSnapshot> loadBudgets) {
        public PlanRevisionSnapshot {
            goals = List.copyOf(goals);
            cycles = List.copyOf(cycles);
            loadBudgets = List.copyOf(loadBudgets);
        }
    }

    record GoalSnapshot(UUID id, String perspective, String category, String title,
                        int priority, String status, LocalDate targetDate,
                        List<GoalOutcomeSnapshot> outcomes) {
        public GoalSnapshot { outcomes = List.copyOf(outcomes); }
    }

    record GoalOutcomeSnapshot(UUID id, String metricCode, BigDecimal baseline,
                               BigDecimal target, String unit, String measurementMethod,
                               String evidenceSource) {
    }

    record CycleSnapshot(UUID id, int sequenceNumber, String name, LocalDate startDate,
                         LocalDate endDate, String phaseIntent, String phaseGoal,
                         List<MicrocycleSnapshot> microcycles) {
        public CycleSnapshot { microcycles = List.copyOf(microcycles); }
    }

    record MicrocycleSnapshot(UUID id, int sequenceNumber, String name, LocalDate startDate,
                              LocalDate endDate, String phaseIntent, String phaseGoal,
                              List<SessionSnapshot> sessions) {
        public MicrocycleSnapshot { sessions = List.copyOf(sessions); }
    }

    record SessionSnapshot(UUID id, String title, LocalDate scheduledDate,
                           Instant availableFrom, Instant availableTo,
                           int expectedDurationMinutes, String status,
                           List<PrescriptionSnapshot> prescriptions, List<SessionVariantSnapshot> variants) {
        public SessionSnapshot { prescriptions = List.copyOf(prescriptions); variants = List.copyOf(variants); }
        /** Compatibility constructor for consumers which predate approved session variants. */
        public SessionSnapshot(UUID id, String title, LocalDate scheduledDate,
                               Instant availableFrom, Instant availableTo,
                               int expectedDurationMinutes, String status,
                               List<PrescriptionSnapshot> prescriptions) {
            this(id, title, scheduledDate, availableFrom, availableTo, expectedDurationMinutes,
                    status, prescriptions, List.of());
        }
    }

    record SessionVariantSnapshot(UUID id, String type, Integer expectedDurationMinutes,
                                  List<SessionVariantItemSnapshot> items) {
        public SessionVariantSnapshot { items = List.copyOf(items); }
    }

    record SessionVariantItemSnapshot(UUID id, UUID basePrescriptionId, int position,
                                      Integer overrideSets, Integer overrideRepetitions,
                                      Integer overrideDurationSeconds, Integer overrideContacts) {
    }

    record PrescriptionSnapshot(
            UUID id, UUID exerciseVersionId, int position, String side, String doseType,
            Integer sets, Integer repetitions, Integer durationSeconds,
            BigDecimal distanceMeters, Integer contacts,
            BigDecimal externalLoadValue, String externalLoadUnit,
            String intensityType, BigDecimal intensityValue, String intensityZone,
            String tempo, String rangeOfMotion, Integer restSeconds,
            String substituteGroup, String notes) {
    }

    record LoadBudgetSnapshot(UUID id, String channel, BigDecimal low, BigDecimal high,
                              String unit, String action) {
    }
}
