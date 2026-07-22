package com.motionecosystem.specialist;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpecialistWorklistItemRepository extends JpaRepository<SpecialistWorklistItem, UUID> {
    Optional<SpecialistWorklistItem> findByDeduplicationKey(String deduplicationKey);
    Optional<SpecialistWorklistItem> findByDeduplicationKeyAndStatusIn(String deduplicationKey, List<String> statuses);
    List<SpecialistWorklistItem> findByParticipantAccountIdOrderByUpdatedAtDesc(UUID participantAccountId);
}
interface ParticipantIssueRepository extends JpaRepository<ParticipantIssue, UUID> { Optional<ParticipantIssue> findByWorklistItemId(UUID worklistItemId); }
interface ParticipantIssueReplyRepository extends JpaRepository<ParticipantIssueReply, UUID> {
    List<ParticipantIssueReply> findByParticipantIssueIdOrderByCreatedAtAsc(UUID participantIssueId);
}

@Entity @Table(name = "worklist_item", schema = "specialist")
class SpecialistWorklistItem {
    @Id UUID id; UUID participantAccountId; UUID planRevisionId; String category; String priority; String reasonCode;
    String minimalData; String policyVersionCode; String deduplicationKey; String status; Instant createdAt; Instant updatedAt;
    Instant acknowledgedAt; Instant snoozedUntil; Instant resolvedAt; String usefulnessOutcome; @Version long version;
    protected SpecialistWorklistItem() { }
    SpecialistWorklistItem(UUID participant, UUID plan, String category, String priority, String reason, String data, String policy, String key, Instant now) {
        id=UUID.randomUUID(); participantAccountId=participant; planRevisionId=plan; this.category=category; this.priority=priority; reasonCode=reason; minimalData=data; policyVersionCode=policy; deduplicationKey=key; status="OPEN"; createdAt=now; updatedAt=now;
    }
    void refresh(String priority, String reason, String data, String policy, Instant now) { this.priority=priority; reasonCode=reason; minimalData=data; policyVersionCode=policy; updatedAt=now; if ("RESOLVED".equals(status)) { status="OPEN"; resolvedAt=null; usefulnessOutcome=null; } }
    void acknowledge(Instant now) { if (!"RESOLVED".equals(status)) { status="ACKNOWLEDGED"; acknowledgedAt=now; updatedAt=now; } }
    void snooze(Instant until, Instant now) { if (until == null || !until.isAfter(now)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "snoozedUntil must be in the future"); if (!"RESOLVED".equals(status)) { status="SNOOZED"; snoozedUntil=until; updatedAt=now; } }
    void resolve(String outcome, Instant now) { status="RESOLVED"; resolvedAt=now; usefulnessOutcome=outcome == null || outcome.isBlank() ? null : outcome.trim(); updatedAt=now; }
}
@Entity @Table(name = "participant_issue", schema = "specialist")
class ParticipantIssue { @Id UUID id; UUID participantAccountId; UUID worklistItemId; String problemCode; String shortText; Instant createdAt; protected ParticipantIssue() { } ParticipantIssue(UUID participant, UUID item, String problem, String text, Instant now) { id=UUID.randomUUID(); participantAccountId=participant; worklistItemId=item; problemCode=problem; shortText=text; createdAt=now; } }
@Entity @Table(name = "participant_issue_reply", schema = "specialist")
class ParticipantIssueReply { @Id UUID id; UUID participantIssueId; UUID specialistAccountId; String shortText; Instant createdAt; protected ParticipantIssueReply() { } ParticipantIssueReply(UUID issue, UUID specialist, String text, Instant now) { id=UUID.randomUUID(); participantIssueId=issue; specialistAccountId=specialist; shortText=text; createdAt=now; } }
