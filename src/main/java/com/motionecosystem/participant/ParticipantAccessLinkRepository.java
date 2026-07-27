package com.motionecosystem.participant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ParticipantAccessLinkRepository extends JpaRepository<ParticipantAccessLink, UUID> { Optional<ParticipantAccessLink> findByParticipantId(UUID participantId); }
