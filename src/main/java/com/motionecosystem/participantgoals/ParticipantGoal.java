package com.motionecosystem.participantgoals;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "participant_goal", schema = "participant_goals")
class ParticipantGoal {
    enum Category { PERFORMANCE, FUNCTIONAL, GENERAL_FITNESS }
    enum Status { ACTIVE, ACHIEVED, CANCELLED }
    @Id UUID id;
    @Column(name = "participant_id", nullable = false) UUID participantId;
    @Column(name = "specialist_account_id", nullable = false) UUID specialistAccountId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) Category category;
    @Column(nullable = false, length = 160) String title;
    @Column(length = 2000) String description;
    @Column(nullable = false) int priority;
    @Column(name = "target_date") LocalDate targetDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false) Status status;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Column(name = "achieved_at") Instant achievedAt;
    @Column(name = "cancelled_at") Instant cancelledAt;
    @Version long version;
    protected ParticipantGoal() { }
    ParticipantGoal(UUID participantId, UUID specialistId, Category category, String title, String description, int priority, LocalDate targetDate, Instant now) {
        id = UUID.randomUUID(); this.participantId = participantId; specialistAccountId = specialistId; this.category = category; this.title = title; this.description = description; this.priority = priority; this.targetDate = targetDate; status = Status.ACTIVE; createdAt = now; updatedAt = now;
    }
    void update(String title, String description, int priority, LocalDate targetDate, Instant now) { this.title = title; this.description = description; this.priority = priority; this.targetDate = targetDate; updatedAt = now; }
    void achieve(Instant now) { status = Status.ACHIEVED; achievedAt = now; updatedAt = now; }
    void cancel(Instant now) { status = Status.CANCELLED; cancelledAt = now; updatedAt = now; }
}
