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
    @Column(name = "participant_account_id", nullable = false) UUID participantAccountId;
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
            String location, String purpose, UUID createdBy, Instant now) {
        id = UUID.randomUUID(); specialistAccountId = specialist; participantAccountId = participant;
        startsAt = starts; endsAt = ends; this.type = type; status = Status.SCHEDULED; locationMode = mode;
        this.location = location; shortPurpose = purpose; createdByAccountId = createdBy; createdAt = now; updatedAt = now;
    }
    void update(Instant starts, Instant ends, Type type, LocationMode mode, String location, String purpose, Instant now) {
        startsAt = starts; endsAt = ends; this.type = type; locationMode = mode; this.location = location; shortPurpose = purpose; updatedAt = now;
    }
    void cancel(Instant now) { status = Status.CANCELLED; updatedAt = now; }
    void noShow(Instant now) { status = Status.NO_SHOW; updatedAt = now; }
}
