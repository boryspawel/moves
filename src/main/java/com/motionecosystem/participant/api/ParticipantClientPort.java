package com.motionecosystem.participant.api;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/** Purpose-built owner port for specialist client-record workflows. */
public interface ParticipantClientPort {

    ClientRecord create(CreateCommand command);

    Optional<ClientRecord> find(UUID participantId);

    Optional<ClientRecord> update(UUID participantId, UpdateCommand command);

    Optional<ClientRecord> archive(UUID participantId, Instant archivedAt);

    Optional<AccessLink> findAccessLink(UUID participantId);

    Optional<UUID> findParticipantIdByPrincipalAccountId(UUID principalAccountId);

    record CreateCommand(String displayName, RelationshipContext relationshipContext, String email, String phone,
                         ZoneId timeZone, UUID createdBySpecialistId, Instant createdAt) {
    }

    record UpdateCommand(String displayName, RelationshipContext relationshipContext, String email, String phone,
                         ZoneId timeZone, Instant updatedAt) {
    }

    record ClientRecord(UUID id, String displayName, RelationshipContext relationshipContext, RecordStatus recordStatus,
                        long version) {
    }

    record AccessLink(UUID principalAccountId, String accessStatus) {
    }

    enum RelationshipContext {
        CLIENT,
        PATIENT,
        ATHLETE
    }

    enum RecordStatus {
        ACTIVE,
        ARCHIVED
    }
}
