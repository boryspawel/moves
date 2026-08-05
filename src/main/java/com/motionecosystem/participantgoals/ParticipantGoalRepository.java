package com.motionecosystem.participantgoals;

import java.util.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

interface ParticipantGoalRepository extends JpaRepository<ParticipantGoal, UUID> {
    @Query("select g from ParticipantGoal g where g.specialistAccountId = :specialistId and g.participantId = :participantId order by case when g.status = 'ACTIVE' then 0 else 1 end, g.priority desc, g.targetDate asc nulls last, g.createdAt asc, g.id asc")
    List<ParticipantGoal> findForSpecialistParticipant(@Param("specialistId") UUID specialistId, @Param("participantId") UUID participantId);
    Optional<ParticipantGoal> findByIdAndSpecialistAccountIdAndParticipantId(UUID id, UUID specialistId, UUID participantId);
    List<ParticipantGoal> findByParticipantIdAndStatus(UUID participantId, ParticipantGoal.Status status);
}
