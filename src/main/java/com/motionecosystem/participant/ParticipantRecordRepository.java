package com.motionecosystem.participant;

import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ParticipantRecordRepository extends JpaRepository<ParticipantRecord, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select record from ParticipantRecord record where record.id = :id")
    Optional<ParticipantRecord> lockById(UUID id);
}
