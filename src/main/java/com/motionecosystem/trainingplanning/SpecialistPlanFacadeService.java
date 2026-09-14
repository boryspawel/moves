package com.motionecosystem.trainingplanning;

import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.PlanMode;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Persistence.RevisionHistoryItem;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.AddCycleCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.AddGoalCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.AddMicrocycleCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.AddSessionCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.CreateDraftCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.CreateRevisionCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.DeleteSessionCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.EditorView;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.PlanListItem;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.UpdateSessionCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.UpdatePeriodCommand;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Task-oriented specialist API: hierarchy is constructed by planning, never by the browser. */
@Service
@RequiredArgsConstructor
public class SpecialistPlanFacadeService {
    private static final String PURPOSE = "Goal-based specialist training";
    private static final String PHASE = "Training period";
    private final TrainingPlanningV2Service planning;

    @Transactional
    public EditorView create(String subject, UUID participantId, CreatePlanCommand command) {
        requireParticipant(participantId, command == null ? null : command.participantId());
        if (command.participantGoalId() == null) throw bad("participantGoalId is required");
        EditorView editor = planning.createDraft(subject, new CreateDraftCommand(participantId, command.name(),
                blankToDefault(command.purpose(), PURPOSE), PlanMode.SPECIALIST,
                blankToDefault(command.phaseIntent(), PHASE), command.validFrom(), command.validTo(), command.actingContext()));
        editor = planning.addGoal(subject, editor.revision().revisionId(),
                new AddGoalCommand(editor.revision().revisionVersion(), command.participantGoalId()));
        editor = planning.addCycle(subject, editor.revision().revisionId(), new AddCycleCommand(
                editor.revision().revisionVersion(), 1, "Training period", command.validFrom(), command.validTo(),
                blankToDefault(command.phaseIntent(), PHASE), PURPOSE));
        UUID cycleId = editor.revision().cycles().getFirst().id();
        return planning.addMicrocycle(subject, editor.revision().revisionId(), new AddMicrocycleCommand(
                editor.revision().revisionVersion(), cycleId, 1, "Training period", command.validFrom(), command.validTo(),
                blankToDefault(command.phaseIntent(), PHASE), PURPOSE));
    }

    public List<PlanListItem> list(String subject, UUID participantId, ActingContext actingContext) {
        return planning.participantPlans(subject, participantId, actingContext);
    }

    public EditorView editor(String subject, UUID participantId, UUID planId, UUID revisionId) {
        EditorView view = planning.editor(subject, revisionId);
        if (!participantId.equals(view.participantId()) || !planId.equals(view.planId())) throw notFound();
        return view;
    }

    public List<RevisionHistoryItem> history(String subject, UUID participantId, UUID planId) {
        List<RevisionHistoryItem> items = planning.history(subject, planId);
        if (items.isEmpty()) throw notFound();
        EditorView view = planning.editor(subject, items.getLast().revisionId());
        if (!participantId.equals(view.participantId())) throw notFound();
        return items;
    }

    @Transactional
    public EditorView createRevision(String subject, UUID participantId, UUID planId, CreateRevisionCommand command) {
        history(subject, participantId, planId);
        return planning.createRevision(subject, planId, command);
    }

    @Transactional
    public EditorView addSession(String subject, UUID participantId, UUID planId, UUID revisionId, SessionCommand command) {
        EditorView view = editor(subject, participantId, planId, revisionId);
        UUID microcycleId = selectMicrocycle(view, command.scheduledDate(), command.availableFrom());
        return planning.addSession(subject, revisionId, new AddSessionCommand(command.expectedVersion(), microcycleId,
                command.title(), command.scheduledDate(), command.availableFrom(), command.availableTo(),
                command.expectedDurationMinutes(), command.exerciseSetVersionId()));
    }

    @Transactional
    public EditorView updateSession(String subject, UUID participantId, UUID planId, UUID revisionId, SessionUpdateCommand command) {
        editor(subject, participantId, planId, revisionId);
        return planning.updateSession(subject, revisionId, new UpdateSessionCommand(command.expectedVersion(), command.sessionId(),
                command.title(), command.scheduledDate(), command.availableFrom(), command.availableTo(),
                command.expectedDurationMinutes(), command.exerciseSetVersionId()));
    }

    @Transactional
    public EditorView deleteSession(String subject, UUID participantId, UUID planId, UUID revisionId,
                                    long expectedVersion, UUID sessionId) {
        editor(subject, participantId, planId, revisionId);
        return planning.deleteSession(subject, revisionId, new DeleteSessionCommand(expectedVersion, sessionId));
    }

    @Transactional
    public EditorView updatePeriod(String subject, UUID participantId, UUID planId, UUID revisionId,
                                   PeriodCommand command) {
        editor(subject, participantId, planId, revisionId);
        return planning.updatePeriod(subject, revisionId,
                new UpdatePeriodCommand(command.expectedVersion(), command.validFrom(), command.validTo()));
    }

    private static UUID selectMicrocycle(EditorView view, LocalDate date, Instant availableFrom) {
        LocalDate effectiveDate = date != null ? date : availableFrom == null ? null : availableFrom.atZone(ZoneOffset.UTC).toLocalDate();
        List<com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort.MicrocycleSnapshot> candidates = view.revision().cycles().stream()
                .flatMap(cycle -> cycle.microcycles().stream())
                .filter(micro -> effectiveDate == null || (!effectiveDate.isBefore(micro.startDate()) && !effectiveDate.isAfter(micro.endDate())))
                .toList();
        if (candidates.size() != 1) {
            throw bad(effectiveDate == null ? "a session schedule is required when revision has multiple stages"
                    : "session schedule does not resolve to one planning stage");
        }
        return candidates.getFirst().id();
    }

    private static void requireParticipant(UUID path, UUID body) {
        if (path == null || (body != null && !path.equals(body))) throw notFound();
    }
    private static String blankToDefault(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "training plan not found"); }

    public record CreatePlanCommand(UUID participantId, String name, String purpose, String phaseIntent,
                                    LocalDate validFrom, LocalDate validTo, UUID participantGoalId,
                                    ActingContext actingContext) { }
    public record SessionCommand(long expectedVersion, String title, LocalDate scheduledDate, Instant availableFrom,
                                 Instant availableTo, Integer expectedDurationMinutes, UUID exerciseSetVersionId) { }
    public record SessionUpdateCommand(long expectedVersion, UUID sessionId, String title, LocalDate scheduledDate,
                                       Instant availableFrom, Instant availableTo, Integer expectedDurationMinutes,
                                       UUID exerciseSetVersionId) { }
    public record PeriodCommand(long expectedVersion, LocalDate validFrom, LocalDate validTo) { }
}
