package com.motionecosystem.participant;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

@Entity
@Table(name = "participant_record", schema = "participant")
public class ParticipantRecord {
    public enum Status { ACTIVE, ARCHIVED }
    public enum RelationshipContext { CLIENT, PATIENT, ATHLETE }
    @Id private UUID id;
    @Column(name = "display_name", nullable = false) private String displayName;
    @Enumerated(EnumType.STRING) @Column(name = "record_status", nullable = false) private Status recordStatus;
    @Enumerated(EnumType.STRING) @Column(name = "relationship_context", nullable = false) private RelationshipContext relationshipContext;
    @Column(name = "time_zone_id") private String timeZoneId;
    private String email;
    private String phone;
    @Column(name = "created_by_specialist_id", nullable = false) private UUID createdBySpecialistId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;
    protected ParticipantRecord() { }
    public ParticipantRecord(String displayName, RelationshipContext context, String email, String phone, ZoneId zone, UUID specialistId, Instant now) {
        id = UUID.randomUUID(); this.displayName = displayName; relationshipContext = context; this.email = email; this.phone = phone;
        timeZoneId = zone == null ? null : zone.getId(); createdBySpecialistId = specialistId; recordStatus = Status.ACTIVE; createdAt = now; updatedAt = now;
    }
    public void update(String name, RelationshipContext context, String emailValue, String phoneValue, ZoneId zone, Instant now) {
        displayName = name; if (context != null) relationshipContext = context; email = emailValue; phone = phoneValue; timeZoneId = zone == null ? null : zone.getId(); updatedAt = now;
    }
    public void archive(Instant now) { recordStatus = Status.ARCHIVED; updatedAt = now; }
    public UUID id() { return id; } public String displayName() { return displayName; } public Status recordStatus() { return recordStatus; }
    public RelationshipContext relationshipContext() { return relationshipContext; } public String timeZoneId() { return timeZoneId; } public String email() { return email; } public String phone() { return phone; } public long version() { return version; }
}
