package com.motionecosystem.trainingplanning;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.trainingplanning.PlannedSession.SessionKind;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TrainingPlanningService {

    private final CurrentAccountService accounts;
    private final TrainingPlanningPersistence persistence;

    @Transactional
    @Deprecated(forRemoval = true)
    public PlanBundle createSpecialistPlan(String subject, CreatePlanCommand command) {
        throw new ResponseStatusException(HttpStatus.GONE,
                "V1 active-plan creation was retired; use the V2 draft and revision workflow");
    }

    @Transactional(readOnly = true)
    public List<SessionView> participantSessions(String subject) {
        CurrentAccount participant = accounts.requireActive(subject);
        requireProfile(participant, ProfileType.PARTICIPANT, "participant profile is required");
        return persistence.findParticipantSessions(participant.id()).stream()
                .map(session -> new SessionView(session.id(), session.title(), session.kind(), session.status(),
                        session.assignedAt(), session.prescriptions().stream()
                        .map(item -> new PrescriptionView(item.id(), item.exerciseVersionId(), item.position(),
                                item.targetSets(), item.targetRepetitions(), item.targetDurationSeconds(),
                                item.targetLoadKg(), item.notes()))
                        .toList()))
                .toList();
    }

    private static void requireProfile(CurrentAccount account, ProfileType expected, String message) {
        if (account.profileType() != expected) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    public record CreatePlanCommand(UUID participantAccountId, String goalName, String planName,
                                    String cycleName, String microcycleName, String sessionTitle,
                                    SessionKind sessionKind, List<PrescriptionCommand> prescriptions) {
    }

    public record PrescriptionCommand(UUID exerciseVersionId, Integer targetSets,
                                      Integer targetRepetitions, Integer targetDurationSeconds,
                                      BigDecimal targetLoadKg, String notes) {
    }

    public record PlanBundle(TrainingGoal goal, TrainingPlan plan, TrainingCycle cycle,
                             Microcycle microcycle, PlannedSession session,
                             List<ExercisePrescription> prescriptions) {
    }

    public record SessionView(UUID id, String title, SessionKind kind,
                              PlannedSession.SessionStatus status, Instant assignedAt,
                              List<PrescriptionView> prescriptions) {
    }

    public record PrescriptionView(UUID id, UUID exerciseVersionId, int position,
                                   Integer targetSets, Integer targetRepetitions,
                                   Integer targetDurationSeconds, BigDecimal targetLoadKg,
                                   String notes) {
    }
}
