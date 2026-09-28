package com.motionecosystem.calendar;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment", schema = "calendar")
public class Appointment {
    public enum Type { TRAINING, PHYSIOTHERAPY, ASSESSMENT, CONSULTATION }
    public enum Status { SCHEDULED, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED, NO_SHOW }
    public enum LocationMode { IN_PERSON, REMOTE, PHONE }

    @Id UUID id;
    @Column(name = "specialist_account_id", nullable = false) UUID specialistAccountId;
    @Column(name = "participant_id", nullable = false) UUID participantId;
    @Column(name = "starts_at", nullable = false) Instant startsAt;
    @Column(name = "ends_at", nullable = false) Instant endsAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) Type type;
    @Enumerated(EnumType.STRING) @Column(nullable = false) Status status;
    @Enumerated(EnumType.STRING) @Column(name = "location_mode", nullable = false) LocationMode locationMode;
    @Column String location;
    @Column(name = "short_purpose") String shortPurpose;
    @Column(name = "planned_session_id") UUID plannedSessionId;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Column(name = "created_by_account_id", nullable = false) UUID createdByAccountId;
    @Version long version;

    protected Appointment() { }
    Appointment(UUID specialist, UUID participant, Instant starts, Instant ends, Type type, LocationMode mode,
            String location, String purpose, UUID plannedSessionId, UUID createdBy, Instant now) {
        id = UUID.randomUUID(); specialistAccountId = specialist; participantId = participant;
        startsAt = starts; endsAt = ends; this.type = type; status = Status.SCHEDULED; locationMode = mode;
        this.location = location; shortPurpose = purpose; this.plannedSessionId = plannedSessionId; createdByAccountId = createdBy; createdAt = now; updatedAt = now;
    }
    Appointment(UUID specialist, UUID participant, Instant starts, Instant ends, Type type, LocationMode mode,
            String location, String purpose, UUID createdBy, Instant now) {
        this(specialist, participant, starts, ends, type, mode, location, purpose, null, createdBy, now);
    }
    void update(Instant starts, Instant ends, Type type, LocationMode mode, String location, String purpose, UUID plannedSessionId, Instant now) {
        startsAt = starts; endsAt = ends; this.type = type; locationMode = mode; this.location = location; shortPurpose = purpose; this.plannedSessionId = plannedSessionId; updatedAt = now;
    }
    void cancel(Instant now) { status = Status.CANCELLED; updatedAt = now; }
    void start(Instant now) { status = Status.IN_PROGRESS; updatedAt = now; }
    void noShow(Instant now) { status = Status.NO_SHOW; updatedAt = now; }
    void complete(Instant now) { status = Status.COMPLETED; updatedAt = now; }
}
