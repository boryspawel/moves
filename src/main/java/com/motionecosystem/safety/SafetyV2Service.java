package com.motionecosystem.safety;

import com.motionecosystem.anatomyreference.api.AnatomyReferenceQueryPort;
import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort.LoadProfile;
import com.motionecosystem.safety.api.SafetyAssessmentPort;
import com.motionecosystem.safety.domain.SafetyRules;
import com.motionecosystem.safety.domain.SafetyRules.ObservationFact;
import com.motionecosystem.safety.domain.SafetyRules.RestrictionFact;
import com.motionecosystem.safety.domain.SafetyRules.SemanticType;
import com.motionecosystem.safety.domain.SafetyRules.SourceType;
import com.motionecosystem.safety.domain.SafetyRules.TargetFact;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.Capability;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort.PlanRevisionSnapshot;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SafetyV2Service implements SafetyAssessmentPort {

    private final RestrictionV2Repository restrictions;
    private final ParticipantRestrictionRepository legacyRestrictions;
    private final SafetyAssessmentRepository assessments;
    private final SafetyFactorRepository factors;
    private final SafetyOverrideRepository overrides;
    private final CurrentAccountService accounts;
    private final AnatomyReferenceQueryPort anatomy;
    private final SpecialistAuthorizationPort authorization;
    private final AuditRecorder audit;
    private final Clock clock;
    private final SafetyRules rules = new SafetyRules();

    @Transactional
    public RestrictionView declareParticipantRestriction(String subject, RestrictionCommand command) {
        var participant = accounts.requireActive(subject);
        if (!participant.hasProfile(ProfileType.PARTICIPANT)) {
            throw forbidden("participant profile is required");
        }
        RestrictionEntity item = create(
                participant.id(),
                SourceType.PARTICIPANT_DECLARED,
                command,
                participant.id(),
                "PARTICIPANT_DECLARATION");
        restrictions.save(item);
        audit.record(subject, "PARTICIPANT_RESTRICTION_DECLARED", "Restriction", item.id);
        return view(item);
    }

    @Transactional
    public RestrictionView reviseParticipantRestriction(
            String subject, UUID restrictionId, RestrictionCommand command) {
        var participant = accounts.requireActive(subject);
        RestrictionEntity previous = requireRestriction(restrictionId);
        requireParticipantOwner(participant.id(), previous);
        RestrictionEntity revision = create(
                participant.id(),
                SourceType.PARTICIPANT_DECLARED,
                command,
                participant.id(),
                "PARTICIPANT_DECLARATION");
        revision.rootId = previous.rootId;
        revision.revisionNumber = previous.revisionNumber + 1;
        revision.supersedesRestrictionId = previous.id;
        previous.status = RestrictionEntity.Status.SUPERSEDED;
        restrictions.save(revision);
        audit.record(subject, "PARTICIPANT_RESTRICTION_REVISED", "Restriction", revision.id);
        return view(revision);
    }

    @Transactional
    public RestrictionView withdrawParticipantRestriction(String subject, UUID restrictionId) {
        var participant = accounts.requireActive(subject);
        RestrictionEntity item = requireRestriction(restrictionId);
        requireParticipantOwner(participant.id(), item);
        item.withdraw();
        audit.record(subject, "PARTICIPANT_RESTRICTION_WITHDRAWN", "Restriction", item.id);
        return view(item);
    }

    @Transactional
    public RestrictionView createPhysiotherapistRestriction(
            UUID actor, UUID participant, ActingContext context, RestrictionCommand command) {
        authorization.requireCapabilities(
                actor,
                participant,
                context,
                Set.of(Capability.SET_CLINICAL_RESTRICTION),
                SpecialistAuthorizationPort.Purpose.CLINICAL_REVIEW);
        RestrictionEntity item = create(
                participant,
                SourceType.PHYSIOTHERAPIST,
                command,
                actor,
                Capability.SET_CLINICAL_RESTRICTION.name());
        restrictions.save(item);
        audit.record(actor.toString(), "CLINICAL_RESTRICTION_CREATED", "Restriction", item.id);
        return view(item);
    }

    @Transactional
    public RestrictionView revisePhysiotherapistRestriction(
            UUID actor, UUID participant, ActingContext context, UUID restrictionId,
            RestrictionCommand command) {
        authorization.requireCapabilities(actor, participant, context,
                Set.of(Capability.SET_CLINICAL_RESTRICTION),
                SpecialistAuthorizationPort.Purpose.CLINICAL_REVIEW);
        RestrictionEntity previous = requireRestriction(restrictionId);
        if (!previous.participantId.equals(participant)
                || previous.sourceType != SourceType.PHYSIOTHERAPIST
                || !previous.authorAccountId.equals(actor)
                || previous.status != RestrictionEntity.Status.ACTIVE) {
            throw forbidden("physiotherapist can revise only their own active clinical restriction");
        }
        RestrictionEntity revision = create(participant, SourceType.PHYSIOTHERAPIST,
                command, actor, Capability.SET_CLINICAL_RESTRICTION.name());
        revision.rootId = previous.rootId;
        revision.revisionNumber = previous.revisionNumber + 1;
        revision.supersedesRestrictionId = previous.id;
        previous.status = RestrictionEntity.Status.SUPERSEDED;
        restrictions.save(revision);
        audit.record(actor.toString(), "CLINICAL_RESTRICTION_REVISED", "Restriction", revision.id);
        return view(revision);
    }

    @Transactional(readOnly = true)
    public List<EffectiveRestrictionView> effectiveRestrictions(
            UUID actor, UUID participant, ActingContext context) {
        authorization.requireCapabilities(actor, participant, context,
                Set.of(Capability.VIEW_EFFECTIVE_RESTRICTION), purpose(context));
        Instant now = clock.instant();
        return restrictions.findByParticipantIdAndStatus(participant, RestrictionEntity.Status.ACTIVE)
                .stream().filter(item -> item.activeAt(now)).map(item -> new EffectiveRestrictionView(
                        item.id, item.validFrom, item.validTo,
                        "ACTIVE_" + item.semanticType.name(), targetView(item))).toList();
    }

    @Transactional(readOnly = true)
    public List<ClinicalRestrictionView> clinicalRestrictions(
            UUID actor, UUID participant, ActingContext context) {
        authorization.requireCapabilities(actor, participant, context,
                Set.of(Capability.VIEW_CLINICAL_RATIONALE),
                SpecialistAuthorizationPort.Purpose.CLINICAL_REVIEW);
        return restrictions.findByParticipantIdOrderByCreatedAt(participant).stream()
                .map(item -> new ClinicalRestrictionView(view(item), item.clinicalRationaleRef)).toList();
    }

    @Transactional(readOnly = true)
    public List<RestrictionView> history(UUID participantId) {
        return restrictions.findByParticipantIdOrderByCreatedAt(participantId).stream()
                .map(SafetyV2Service::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RestrictionView> participantHistory(String subject) {
        var account = accounts.requireActive(subject);
        if (!account.hasProfile(ProfileType.PARTICIPANT)) {
            throw forbidden("participant profile is required");
        }
        return history(account.id());
    }

    @Override
    @Transactional
    public AssessmentSnapshot assess(
            UUID participantAccountId,
            PlanRevisionSnapshot revision,
            LoadProfile loadProfile) {
        requireAssessmentInput(participantAccountId, revision, loadProfile);
        Instant now = clock.instant();
        List<RestrictionEntity> active = restrictions.findByParticipantIdAndStatus(
                        participantAccountId, RestrictionEntity.Status.ACTIVE)
                .stream()
                .filter(item -> item.activeAt(now))
                .toList();
        Map<UUID, Set<UUID>> ancestors = loadProfile.observations().stream()
                .map(item -> item.structureId())
                .distinct()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> anatomy.ancestorPaths(id).stream()
                                .flatMap(path -> path.steps().stream())
                                .map(step -> step.structure().id())
                                .collect(Collectors.toSet())));
        Map<UUID, LocalDate> dates = revision.cycles().stream()
                .flatMap(cycle -> cycle.microcycles().stream())
                .flatMap(microcycle -> microcycle.sessions().stream())
                .filter(session -> session.scheduledDate() != null)
                .collect(Collectors.toMap(session -> session.id(), session -> session.scheduledDate()));
        var evaluation = rules.evaluate(
                active.stream().map(SafetyV2Service::fact).toList(),
                loadProfile.observations().stream().map(item -> new ObservationFact(
                        item.sessionId(),
                        item.structureId(),
                        item.side(),
                        item.channel(),
                        item.observationFamily(),
                        item.unit(),
                        item.low(),
                        item.high(),
                        item.confidence(),
                        item.evidenceGrade())).toList(),
                ancestors,
                dates);
        SafetyAssessmentEntity assessment = new SafetyAssessmentEntity();
        assessment.id = UUID.randomUUID();
        assessment.participantId = participantAccountId;
        assessment.revisionId = revision.revisionId();
        assessment.loadSnapshotId = loadProfile.snapshotId();
        assessment.loadInputChecksum = loadProfile.inputChecksum();
        assessment.loadCalculationVersion =
                loadProfile.algorithmVersion() + "/" + loadProfile.configurationVersion();
        assessment.rulesetCode = SafetyRules.RULESET_CODE;
        assessment.rulesetVersion = SafetyRules.RULESET_VERSION;
        assessment.result = Result.valueOf(evaluation.result().name());
        assessment.restrictionSnapshot = active.stream()
                .map(SafetyV2Service::restrictionSnapshot)
                .sorted()
                .collect(Collectors.joining("\n"));
        assessment.rulesetSnapshot = "SAFETY_V2/1:" + String.join(",",
                "HARD_RESTRICTION_INTERSECTION",
                "EXPLICIT_LIMIT",
                "MINIMUM_RECOVERY",
                "PARTICIPANT_DECLARATION",
                "LOW_CONFIDENCE_MAPPING");
        assessment.loadSnapshot = loadProfile.toString();
        assessment.assessedAt = now;
        evaluation.factors().forEach(factor -> assessment.factors.add(entity(assessment, factor)));
        assessments.save(assessment);
        audit.record("system:safety", "PLAN_SAFETY_ASSESSED", "PlanSafetyAssessment", assessment.id);
        return snapshot(assessment, now);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<AssessmentSnapshot> findAssessment(UUID assessmentId, Instant effectiveAt) {
        Instant at = effectiveAt == null ? clock.instant() : effectiveAt;
        return assessments.findById(assessmentId).map(item -> snapshot(item, at));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isRestrictionSnapshotFresh(
            UUID assessmentId, UUID participantAccountId, Instant effectiveAt) {
        Instant at = effectiveAt == null ? clock.instant() : effectiveAt;
        SafetyAssessmentEntity assessment = assessments.findById(assessmentId).orElse(null);
        if (assessment == null || !assessment.participantId.equals(participantAccountId)) {
            return false;
        }
        String current = restrictions.findByParticipantIdAndStatus(
                        participantAccountId, RestrictionEntity.Status.ACTIVE)
                .stream()
                .filter(item -> item.activeAt(at))
                .map(SafetyV2Service::restrictionSnapshot)
                .sorted()
                .collect(Collectors.joining("\n"));
        return assessment.restrictionSnapshot.equals(current);
    }

    @Transactional
    public OverrideView overrideFactor(
            UUID actor,
            UUID participant,
            ActingContext context,
            UUID assessmentId,
            UUID factorId,
            OverrideCommand command) {
        authorization.requireCapabilities(
                actor,
                participant,
                context,
                Set.of(Capability.OVERRIDE_CLINICAL_BLOCK),
                SpecialistAuthorizationPort.Purpose.CLINICAL_REVIEW);
        SafetyAssessmentEntity assessment = assessments.findById(assessmentId)
                .orElseThrow(() -> notFound("assessment not found"));
        if (!assessment.participantId.equals(participant)) {
            throw forbidden("assessment belongs to another participant");
        }
        SafetyFactorEntity factor = factors.findById(factorId)
                .orElseThrow(() -> notFound("assessment factor not found"));
        if (!factor.assessment.id.equals(assessmentId) || !factor.overridable
                || factor.result != Result.HARD_BLOCK) {
            throw forbidden("this factor cannot be overridden");
        }
        Instant now = clock.instant();
        if (command == null || blank(command.reasonCode()) || blank(command.scope())
                || command.validTo() == null || !command.validTo().isAfter(now)) {
            throw badRequest("override reason, scope and future validity are required");
        }
        SafetyOverrideEntity item = new SafetyOverrideEntity();
        item.id = UUID.randomUUID();
        item.assessmentId = assessmentId;
        item.factorId = factorId;
        item.actorId = actor;
        item.actorCapability = Capability.OVERRIDE_CLINICAL_BLOCK.name();
        item.reasonCode = command.reasonCode().trim();
        item.scope = command.scope().trim();
        item.validFrom = command.validFrom() == null ? now : command.validFrom();
        item.validTo = command.validTo();
        item.createdAt = now;
        overrides.save(item);
        audit.record(actor.toString(), "SAFETY_FACTOR_OVERRIDDEN", "AssessmentFactor", factorId);
        return new OverrideView(item.id, assessmentId, factorId, item.reasonCode,
                item.scope, item.validFrom, item.validTo);
    }

    @Transactional(readOnly = true)
    public LegacyReport legacyReport() {
        return new LegacyReport(
                legacyRestrictions.count(),
                "Legacy participant tags are retained for review and are not converted into Safety V2 restrictions.");
    }

    private RestrictionEntity create(
            UUID participant,
            SourceType sourceType,
            RestrictionCommand command,
            UUID author,
            String capability) {
        if (command == null || command.semanticType() == null || command.target() == null
                || blank(command.participantExplanation())) {
            throw badRequest("semantic type, target and participant-visible explanation are required");
        }
        Instant now = clock.instant();
        Instant from = command.validFrom() == null ? now : command.validFrom();
        if (command.validTo() != null && command.validTo().isBefore(from)) {
            throw badRequest("restriction validity is invalid");
        }
        validateTarget(command.target());
        return RestrictionEntity.initial(
                participant,
                sourceType,
                command.semanticType(),
                author,
                capability,
                limited(command.participantExplanation(), 500, "participant explanation"),
                nullableLimited(command.clinicalRationaleRef(), 500, "clinical rationale reference"),
                from,
                command.validTo(),
                target(command.target()),
                now);
    }

    private void validateTarget(TargetCommand target) {
        if (target.structureId() == null && blank(target.movementPattern())
                && blank(target.channel()) && blank(target.loadCharacteristic())) {
            throw badRequest("restriction target requires a structure, movement pattern, channel or characteristic");
        }
        if (target.structureId() != null && anatomy.findStructure(target.structureId()).isEmpty()) {
            throw badRequest("restriction target structure is unknown");
        }
        if ((target.limitLow() != null || target.limitHigh() != null) && blank(target.unit())) {
            throw badRequest("restriction limit requires a unit");
        }
        if (target.limitLow() != null && target.limitHigh() != null
                && target.limitLow().compareTo(target.limitHigh()) > 0) {
            throw badRequest("restriction limit range is invalid");
        }
        if (target.minimumRecoveryHours() != null && target.minimumRecoveryHours() <= 0) {
            throw badRequest("minimum recovery hours must be positive");
        }
    }

    private static RestrictionTargetEntity target(TargetCommand source) {
        RestrictionTargetEntity target = new RestrictionTargetEntity();
        target.structureId = source.structureId();
        target.movementPattern = normalized(source.movementPattern());
        target.channel = normalized(source.channel());
        target.loadCharacteristic = normalized(source.loadCharacteristic());
        target.side = normalized(source.side());
        target.rangeOfMotion = normalized(source.rangeOfMotion());
        target.contractionType = normalized(source.contractionType());
        target.limitLow = source.limitLow();
        target.limitHigh = source.limitHigh();
        target.unit = normalized(source.unit());
        target.minimumRecoveryHours = source.minimumRecoveryHours();
        return target;
    }

    private static RestrictionFact fact(RestrictionEntity item) {
        return new RestrictionFact(item.id, item.sourceType, item.semanticType, new TargetFact(
                item.target.structureId,
                item.target.movementPattern,
                item.target.channel,
                item.target.loadCharacteristic,
                item.target.side,
                item.target.rangeOfMotion,
                item.target.contractionType,
                item.target.limitLow,
                item.target.limitHigh,
                item.target.unit,
                item.target.minimumRecoveryHours));
    }

    private static SafetyFactorEntity entity(
            SafetyAssessmentEntity assessment, SafetyRules.Factor factor) {
        SafetyFactorEntity item = new SafetyFactorEntity();
        item.id = UUID.randomUUID();
        item.assessment = assessment;
        item.result = Result.valueOf(factor.result().name());
        item.ruleCode = factor.ruleCode();
        item.targetRef = factor.targetRef();
        item.channel = factor.channel();
        item.observedLow = factor.observedLow();
        item.observedHigh = factor.observedHigh();
        item.thresholdLow = factor.thresholdLow();
        item.thresholdHigh = factor.thresholdHigh();
        item.unit = factor.unit();
        item.explanationCode = factor.explanationCode();
        item.evidenceGrade = factor.evidenceGrade();
        item.overridable = factor.overridable();
        return item;
    }

    private AssessmentSnapshot snapshot(SafetyAssessmentEntity item, Instant effectiveAt) {
        Set<UUID> activeOverrides = overrides.findByAssessmentId(item.id).stream()
                .filter(override -> override.activeAt(effectiveAt))
                .map(override -> override.factorId)
                .collect(Collectors.toSet());
        List<FactorSnapshot> factorViews = item.factors.stream()
                .map(factor -> new FactorSnapshot(
                        factor.id,
                        factor.result,
                        factor.ruleCode,
                        factor.targetRef,
                        factor.channel,
                        factor.observedLow,
                        factor.observedHigh,
                        factor.thresholdLow,
                        factor.thresholdHigh,
                        factor.unit,
                        factor.explanationCode,
                        factor.evidenceGrade,
                        factor.overridable,
                        activeOverrides.contains(factor.id)))
                .toList();
        Result effective = factorViews.stream()
                .filter(factor -> !factor.activelyOverridden())
                .map(FactorSnapshot::result)
                .max(Comparator.comparingInt(Result::ordinal))
                .orElse(Result.PASS);
        return new AssessmentSnapshot(
                item.id,
                item.participantId,
                item.revisionId,
                item.loadSnapshotId,
                item.loadCalculationVersion,
                item.rulesetCode,
                item.rulesetVersion,
                item.result,
                effective,
                item.assessedAt,
                factorViews);
    }

    private static RestrictionView view(RestrictionEntity item) {
        return new RestrictionView(
                item.id,
                item.rootId,
                item.revisionNumber,
                item.supersedesRestrictionId,
                item.participantId,
                item.sourceType,
                item.semanticType,
                item.status.name(),
                item.validFrom,
                item.validTo,
                item.authorCapability,
                item.participantExplanation,
                targetView(item));
    }

    private static TargetView targetView(RestrictionEntity item) {
        return new TargetView(item.target.structureId, item.target.movementPattern,
                item.target.channel, item.target.loadCharacteristic, item.target.side,
                item.target.rangeOfMotion, item.target.contractionType, item.target.limitLow,
                item.target.limitHigh, item.target.unit, item.target.minimumRecoveryHours);
    }

    private static SpecialistAuthorizationPort.Purpose purpose(ActingContext context) {
        if (context == null || context.role() == null) throw badRequest("acting context is required");
        return context.role() == SpecialistAuthorizationPort.ProfessionalRole.TRAINER
                ? SpecialistAuthorizationPort.Purpose.PERFORMANCE_PLANNING
                : SpecialistAuthorizationPort.Purpose.FUNCTIONAL_RECOVERY;
    }

    private static String restrictionSnapshot(RestrictionEntity item) {
        return item.id + "|" + item.rootId + "|" + item.revisionNumber + "|"
                + item.sourceType + "|" + item.semanticType + "|" + fact(item).target();
    }

    private static void requireAssessmentInput(
            UUID participant, PlanRevisionSnapshot revision, LoadProfile load) {
        if (participant == null || revision == null || load == null
                || !participant.equals(revision.participantAccountId())
                || !revision.revisionId().equals(load.revisionId())) {
            throw badRequest("participant, revision and matching saved load snapshot are required");
        }
    }

    private RestrictionEntity requireRestriction(UUID id) {
        return restrictions.findById(id).orElseThrow(() -> notFound("restriction not found"));
    }

    private static void requireParticipantOwner(UUID participant, RestrictionEntity item) {
        if (!item.participantId.equals(participant)
                || item.sourceType != SourceType.PARTICIPANT_DECLARED
                || item.status != RestrictionEntity.Status.ACTIVE) {
            throw forbidden("participants can change only their own active declarations");
        }
    }

    private static String normalized(String value) {
        return blank(value) ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static String nullableLimited(String value, int max, String field) {
        return blank(value) ? null : limited(value, max, field);
    }

    private static String limited(String value, int max, String field) {
        String result = Objects.requireNonNull(value).trim();
        if (result.isEmpty() || result.length() > max) {
            throw badRequest(field + " is invalid");
        }
        return result;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    public record TargetCommand(
            UUID structureId,
            String movementPattern,
            String channel,
            String loadCharacteristic,
            String side,
            String rangeOfMotion,
            String contractionType,
            BigDecimal limitLow,
            BigDecimal limitHigh,
            String unit,
            Integer minimumRecoveryHours) {
    }

    public record RestrictionCommand(
            SemanticType semanticType,
            Instant validFrom,
            Instant validTo,
            String participantExplanation,
            String clinicalRationaleRef,
            TargetCommand target) {
    }

    public record TargetView(
            UUID structureId,
            String movementPattern,
            String channel,
            String loadCharacteristic,
            String side,
            String rangeOfMotion,
            String contractionType,
            BigDecimal limitLow,
            BigDecimal limitHigh,
            String unit,
            Integer minimumRecoveryHours) {
    }

    /** Participant-safe view intentionally excludes clinicalRationaleRef. */
    public record RestrictionView(
            UUID id,
            UUID rootId,
            int revisionNumber,
            UUID supersedesRestrictionId,
            UUID participantId,
            SourceType sourceType,
            SemanticType semanticType,
            String status,
            Instant validFrom,
            Instant validTo,
            String authorCapability,
            String participantExplanation,
            TargetView target) {
    }

    /** Planning-safe envelope: no source, diagnosis or clinical rationale is exposed. */
    public record EffectiveRestrictionView(UUID restrictionId, Instant validFrom, Instant validTo,
                                           String explanationCode, TargetView target) { }

    public record ClinicalRestrictionView(RestrictionView restriction, String clinicalRationaleRef) { }

    public record OverrideCommand(
            String reasonCode,
            String scope,
            Instant validFrom,
            Instant validTo) {
    }

    public record OverrideView(
            UUID id,
            UUID assessmentId,
            UUID factorId,
            String reasonCode,
            String scope,
            Instant validFrom,
            Instant validTo) {
    }

    public record LegacyReport(long unmappedParticipantTags, String notice) {
    }
}
