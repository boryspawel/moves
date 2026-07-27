package com.motionecosystem.participant;

import com.motionecosystem.participant.api.ParticipantClientPort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class ParticipantClientService implements ParticipantClientPort {

    private final ParticipantRecordRepository records;
    private final ParticipantAccessLinkRepository links;

    @Override
    public ClientRecord create(CreateCommand command) {
        ParticipantRecord record = records.save(new ParticipantRecord(
                command.displayName(), ParticipantRecord.RelationshipContext.valueOf(command.relationshipContext().name()),
                command.email(), command.phone(), command.timeZone(), command.createdBySpecialistId(), command.createdAt()));
        return view(record);
    }

    @Override
    public Optional<ClientRecord> find(UUID participantId) {
        return records.findById(participantId).map(ParticipantClientService::view);
    }

    @Override
    public Optional<ClientRecord> update(UUID participantId, UpdateCommand command) {
        return records.findById(participantId).map(record -> {
            record.update(command.displayName(), ParticipantRecord.RelationshipContext.valueOf(command.relationshipContext().name()),
                    command.email(), command.phone(), command.timeZone(), command.updatedAt());
            return view(record);
        });
    }

    @Override
    public Optional<ClientRecord> archive(UUID participantId, Instant archivedAt) {
        return records.findById(participantId).map(record -> {
            record.archive(archivedAt);
            return view(record);
        });
    }

    @Override
    public Optional<AccessLink> findAccessLink(UUID participantId) {
        return links.findByParticipantId(participantId)
                .map(link -> new AccessLink(link.principalAccountId(), link.accessStatus().name()));
    }

    @Override
    public Optional<UUID> findParticipantIdByPrincipalAccountId(UUID principalAccountId) {
        return links.findByPrincipalAccountId(principalAccountId)
                .filter(link -> link.accessStatus() == ParticipantAccessLink.Status.ACTIVE)
                .map(ParticipantAccessLink::participantId);
    }

    private static ClientRecord view(ParticipantRecord record) {
        return new ClientRecord(record.id(), record.displayName(),
                RelationshipContext.valueOf(record.relationshipContext().name()),
                RecordStatus.valueOf(record.recordStatus().name()), record.version());
    }
}
