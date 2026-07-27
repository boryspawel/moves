package com.motionecosystem.participant;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name = "participant_access_link", schema = "participant")
public class ParticipantAccessLink {
    public enum Status { CLAIMED, ACTIVE, SUSPENDED }
    @Id private UUID id;
    @Column(name = "participant_id", nullable = false) private UUID participantId;
    @Column(name = "principal_account_id", nullable = false) private UUID principalAccountId;
    @Enumerated(EnumType.STRING) @Column(name = "access_status", nullable = false) private Status accessStatus;
    protected ParticipantAccessLink() { }
    public UUID participantId() { return participantId; }
    public UUID principalAccountId() { return principalAccountId; }
    public Status accessStatus() { return accessStatus; }
}
