package com.motionecosystem.specialist;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.analytics.adherencemetrics.AdherenceMetricsService;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.specialist.api.AdherenceSpecialistSignalPort;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.Capability;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.Purpose;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Specialist-owned, deliberately small projection of signals that need a human decision. */
@Service
@RequiredArgsConstructor
class SpecialistWorklistService {
    private static final List<String> ACTIVE_STATUSES = List.of("OPEN", "ACKNOWLEDGED", "SNOOZED");
    private static final Set<String> CATEGORIES = Set.of("ESCALATING_SYMPTOMS", "TECHNIQUE_UNCERTAINTY",
            "REPEATED_BARRIERS", "FAILED_ATTEMPTS", "NO_RETURN_AFTER_GAP", "PLAN_MISMATCH",
            "POST_24H_FOLLOW_UP", "PARTICIPANT_ISSUE");
    private final SpecialistWorklistItemRepository items;
    private final ParticipantIssueRepository issues;
    private final ParticipantIssueReplyRepository replies;
    private final ParticipantSpecialistRelationshipRepository relationships;
    private final CurrentAccountService accounts;
    private final SpecialistAuthorizationPort authorization;
    private final AuditRecorder audit;
    private final Clock clock;
    private final JdbcTemplate jdbc;
    private final AdherenceMetricsService metrics;

    @Transactional
    void signal(AdherenceSpecialistSignalPort.WorklistSignal signal) {
        if (signal == null || signal.participantAccountId() == null || !CATEGORIES.contains(signal.category())) return;
        String key = key(signal.participantAccountId(), signal.planRevisionId(), signal.category(), signal.reasonCode());
        Instant now = clock.instant();
        SpecialistWorklistItem item = activeItem(key).orElseGet(() -> createActiveItem(
                signal.participantAccountId(), signal.planRevisionId(), signal.category(), priority(signal.priority()),
                signal.reasonCode(), minimalData(signal.reasonCode()), policy(signal.policyVersion()), key, now));
        item.refresh(priority(signal.priority()), signal.reasonCode(), minimalData(signal.reasonCode()), policy(signal.policyVersion()), now);
        items.save(item);
    }

    @Transactional(readOnly = true)
    List<WorklistItemView> list(String subject, ActingContext context, Purpose purpose) {
        UUID specialist = specialist(subject);
        return relationships.findBySpecialistAccountIdAndStatus(specialist, ParticipantSpecialistRelationship.Status.ACTIVE).stream()
                .flatMap(relationship -> items.findByParticipantAccountIdOrderByUpdatedAtDesc(relationship.participantAccountId()).stream()
                        .filter(item -> visible(item, specialist, context, purpose))
                        .map(this::viewFor))
                .toList();
    }

    @Transactional
    WorklistItemView action(String subject, UUID itemId, ActingContext context, Purpose purpose, ActionCommand command) {
        UUID specialist = specialist(subject);
        SpecialistWorklistItem item = item(itemId);
        authorize(specialist, item.participantAccountId, context, purpose, Capability.VIEW_ADHERENCE_WORKLIST);
        String action = command == null || command.action() == null ? "" : command.action().trim();
        Instant now = clock.instant();
        switch (action) {
            case "ACKNOWLEDGE" -> item.acknowledge(now);
            case "SNOOZE" -> item.snooze(command.snoozedUntil(), now);
            case "RESOLVE" -> item.resolve(command.usefulnessOutcome(), now);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported worklist action");
        }
        audit.record(subject, "SPECIALIST_WORKLIST_" + action, "SpecialistWorklistItem", item.id);
        return viewFor(items.save(item));
    }

    @Transactional
    WorklistItemView reportIssue(String subject, ParticipantIssueCommand command) {
        UUID participant = participant(subject);
        if (command == null || blank(command.problemCode())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "problemCode is required");
        String text = shortText(command.shortText());
        Instant now = clock.instant();
        String problemCode = command.problemCode().trim();
        String key = key(participant, null, "PARTICIPANT_ISSUE", problemCode);
        SpecialistWorklistItem item = activeItem(key).orElseGet(() -> createActiveItem(participant, null,
                "PARTICIPANT_ISSUE", "MEDIUM", problemCode, "participant-reported-problem", "PARTICIPANT_ISSUE_V1", key, now));
        ParticipantIssue issue = new ParticipantIssue(participant, item.id, problemCode, text, now);
        if (jdbc.update("""
                INSERT INTO specialist.participant_issue
                    (id, participant_account_id, worklist_item_id, problem_code, short_text, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (worklist_item_id) DO NOTHING
                """, issue.id, participant, item.id, problemCode, text, Timestamp.from(now)) == 1) {
            audit.record(subject, "PARTICIPANT_ISSUE_REPORTED", "ParticipantIssue", issue.id);
        }
        return viewFor(item);
    }

    @Transactional
    ReplyView reply(String subject, UUID itemId, ActingContext context, Purpose purpose, ReplyCommand command) {
        UUID specialist = specialist(subject);
        SpecialistWorklistItem item = item(itemId);
        authorize(specialist, item.participantAccountId, context, purpose, Capability.RESPOND_TO_PARTICIPANT_ISSUE);
        ParticipantIssue issue = issues.findByWorklistItemId(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "worklist item has no participant issue"));
        String text = requiredText(command == null ? null : command.shortText());
        ParticipantIssueReply reply = replies.save(new ParticipantIssueReply(issue.id, specialist, text, clock.instant()));
        item.acknowledge(clock.instant());
        audit.record(subject, "SPECIALIST_REPLIED_TO_PARTICIPANT_ISSUE", "ParticipantIssueReply", reply.id);
        metrics.record(item.participantAccountId, "WORKLIST_REPLIED", reply.id, item.planRevisionId, null, null,
                "WORKLIST_REPLY_V1", null);
        return new ReplyView(reply.id, reply.shortText, reply.createdAt);
    }

    private boolean visible(SpecialistWorklistItem item, UUID specialist, ActingContext context, Purpose purpose) {
        try { authorize(specialist, item.participantAccountId, context, purpose, Capability.VIEW_ADHERENCE_WORKLIST); return true; }
        catch (ResponseStatusException denied) { return false; }
    }
    private void authorize(UUID specialist, UUID participant, ActingContext context, Purpose purpose, Capability capability) {
        authorization.requireCapabilities(specialist, participant, context, Set.of(capability), purpose);
    }
    private UUID specialist(String subject) { var account = accounts.requireActive(subject); if (!account.hasProfile(ProfileType.SPECIALIST)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "specialist profile is required"); return account.id(); }
    private UUID participant(String subject) { var account = accounts.requireActive(subject); if (!account.hasProfile(ProfileType.PARTICIPANT)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "participant profile is required"); return account.id(); }
    private SpecialistWorklistItem item(UUID id) { return items.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "worklist item not found")); }
    private java.util.Optional<SpecialistWorklistItem> activeItem(String key) {
        return items.findByDeduplicationKeyAndStatusIn(key, ACTIVE_STATUSES);
    }
    private SpecialistWorklistItem createActiveItem(UUID participant, UUID plan, String category, String priority,
            String reason, String data, String policy, String key, Instant now) {
        SpecialistWorklistItem candidate = new SpecialistWorklistItem(participant, plan, category, priority, reason, data, policy, key, now);
        if (jdbc.update("""
                INSERT INTO specialist.worklist_item
                    (id, participant_account_id, plan_revision_id, category, priority, reason_code, minimal_data,
                     policy_version_code, deduplication_key, status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, 0)
                ON CONFLICT (deduplication_key) WHERE status IN ('OPEN', 'ACKNOWLEDGED', 'SNOOZED') DO NOTHING
                """, candidate.id, participant, plan, category, priority, reason, data, policy, key,
                Timestamp.from(now), Timestamp.from(now)) == 1) {
            return candidate;
        }
        return activeItem(key).orElseThrow(() -> new IllegalStateException("active worklist item was not found after deduplication conflict"));
    }
    private WorklistItemView viewFor(SpecialistWorklistItem item) {
        return issues.findByWorklistItemId(item.id).map(issue -> view(item, issue.shortText,
                replies.findByParticipantIssueIdOrderByCreatedAtAsc(issue.id).stream()
                        .map(reply -> new ReplyView(reply.id, reply.shortText, reply.createdAt)).toList()))
                .orElseGet(() -> view(item, null, List.of()));
    }
    private static String key(UUID participant, UUID plan, String category, String reason) { return participant + ":" + (plan == null ? "none" : plan) + ":" + category + ":" + reason; }
    private static String policy(String value) { return blank(value) ? "ADHERENCE_WORKLIST_V1" : value.trim(); }
    private static String priority(String value) { return Set.of("LOW", "MEDIUM", "HIGH").contains(value) ? value : "MEDIUM"; }
    private static String minimalData(String reason) { return blank(reason) ? "signal" : reason.trim(); }
    private static String shortText(String value) { if (value == null || value.isBlank()) return null; return requiredText(value); }
    private static String requiredText(String value) { if (blank(value) || value.trim().length() > 500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shortText must contain 1-500 characters"); return value.trim(); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }

    record ActionCommand(String action, Instant snoozedUntil, String usefulnessOutcome) { }
    record ParticipantIssueCommand(String problemCode, String shortText) { }
    record ReplyCommand(String shortText) { }
    record WorklistItemView(UUID id, UUID participantAccountId, UUID planRevisionId, String category, String priority,
                            String reasonCode, String minimalData, String policyVersion, String status, Instant createdAt, Instant snoozedUntil,
                            String issueText, List<ReplyView> replies) { }
    record ReplyView(UUID id, String shortText, Instant createdAt) { }
    private static WorklistItemView view(SpecialistWorklistItem item, String issueText, List<ReplyView> replies) { return new WorklistItemView(item.id, item.participantAccountId, item.planRevisionId, item.category, item.priority, item.reasonCode, item.minimalData, item.policyVersionCode, item.status, item.createdAt, item.snoozedUntil, issueText, replies); }
}
