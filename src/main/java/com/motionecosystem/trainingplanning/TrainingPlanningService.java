package com.motionecosystem.trainingplanning;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.exercisecatalog.api.ExerciseCatalogQueryPort;
import com.motionecosystem.identityaccess.api.ActiveParticipantPort;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.specialist.SpecialistRelationshipService;
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
    private final ActiveParticipantPort participants;
    private final SpecialistRelationshipService relationships;
    private final ExerciseCatalogQueryPort catalog;
    private final TrainingPlanningPersistence persistence;
    private final AuditRecorder audit;
    private final Clock clock;

    @Transactional
    public PlanBundle createSpecialistPlan(String subject, CreatePlanCommand command) {
        CurrentAccount specialist = accounts.requireActive(subject);
        requireProfile(specialist, ProfileType.SPECIALIST, "specialist profile is required");
        if (command == null || command.participantAccountId() == null) {
            throw badRequest("participant account is required");
        }
        requireParticipant(command.participantAccountId());
        relationships.requireActive(specialist.id(), command.participantAccountId());
        List<PrescriptionCommand> requested = validatePrescriptions(command.prescriptions());
        requested.forEach(item -> catalog.findPublishedVersion(item.exerciseVersionId())
                .orElseThrow(() -> badRequest("prescription requires a published exercise version")));

        Instant now = clock.instant();
        TrainingGoal goal = new TrainingGoal(UUID.randomUUID(), command.participantAccountId(),
                text(command.goalName(), "goal name"), specialist.id(), now);
        TrainingPlan plan = new TrainingPlan(UUID.randomUUID(), goal.id(), command.participantAccountId(),
                specialist.id(), text(command.planName(), "plan name"),
                TrainingPlan.PlanMode.SPECIALIST_ASSIGNED, TrainingPlan.PlanStatus.ACTIVE, now);
        TrainingCycle cycle = new TrainingCycle(UUID.randomUUID(), plan.id(), 1,
                text(command.cycleName(), "cycle name"));
        Microcycle microcycle = new Microcycle(UUID.randomUUID(), cycle.id(), 1,
                text(command.microcycleName(), "microcycle name"));
        SessionKind kind = command.sessionKind() == null ? SessionKind.SELF_GUIDED : command.sessionKind();
        if (kind == SessionKind.OFFLINE_APPOINTMENT) {
            throw badRequest("offline appointments belong to the calendar and cannot be planned as sessions");
        }
        PlannedSession session = new PlannedSession(UUID.randomUUID(), microcycle.id(),
                command.participantAccountId(), text(command.sessionTitle(), "session title"), kind,
                PlannedSession.SessionStatus.ASSIGNED, now);

        List<ExercisePrescription> prescriptions = java.util.stream.IntStream.range(0, requested.size())
                .mapToObj(index -> prescription(session.id(), index + 1, requested.get(index)))
                .toList();
        persistence.save(goal, plan, cycle, microcycle, session, prescriptions);
        audit.record(subject, "TRAINING_PLAN_ASSIGNED", "TrainingPlan", plan.id());
        return new PlanBundle(goal, plan, cycle, microcycle, session, prescriptions);
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

    private void requireParticipant(UUID accountId) {
        if (participants.findActiveParticipant(accountId).isEmpty()) {
            throw badRequest("active participant account is required");
        }
    }

    private static ExercisePrescription prescription(UUID sessionId, int position, PrescriptionCommand command) {
        if (command.targetSets() != null && command.targetSets() <= 0
                || command.targetRepetitions() != null && command.targetRepetitions() <= 0
                || command.targetDurationSeconds() != null && command.targetDurationSeconds() <= 0
                || command.targetLoadKg() != null && command.targetLoadKg().signum() < 0) {
            throw badRequest("prescription targets are outside range");
        }
        String notes = optionalText(command.notes(), 500, "prescription notes");
        return new ExercisePrescription(UUID.randomUUID(), sessionId, command.exerciseVersionId(), position,
                command.targetSets(), command.targetRepetitions(), command.targetDurationSeconds(),
                command.targetLoadKg(), notes);
    }

    private static List<PrescriptionCommand> validatePrescriptions(List<PrescriptionCommand> requested) {
        if (requested == null || requested.isEmpty()) {
            throw badRequest("at least one exercise prescription is required");
        }
        requested.forEach(item -> {
            if (item == null || item.exerciseVersionId() == null) {
                throw badRequest("every exercise prescription must reference an exercise version");
            }
        });
        return List.copyOf(requested);
    }

    private static void requireProfile(CurrentAccount account, ProfileType expected, String message) {
        if (account.profileType() != expected) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    private static String text(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 160) {
            throw badRequest(field + " is required and must not exceed 160 characters");
        }
        return normalized;
    }

    private static String optionalText(String value, int max, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw badRequest(field + " is too long");
        }
        return normalized;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
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
