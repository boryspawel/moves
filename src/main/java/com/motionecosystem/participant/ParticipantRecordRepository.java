package com.motionecosystem.participant;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ParticipantRecordRepository extends JpaRepository<ParticipantRecord, UUID> { }
