package com.motionecosystem.adherence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "recovery_episode", schema = "adherence")
class RecoveryEpisode {
    @Id UUID id;
    @Column(name = "participant_account_id", nullable = false) UUID participantAccountId;
    @Column(nullable = false) String status;
    @Column(name = "policy_version_code", nullable = false) String policyVersionCode;
    @Column(name = "opened_at", nullable = false) Instant openedAt;
    @Column(name = "participant_time_zone", nullable = false) String participantTimeZone;
    @Column(name = "detected_local_date", nullable = false) LocalDate detectedLocalDate;
    @Column(name = "plan_revision_id_at_opening", nullable = false) UUID planRevisionIdAtOpening;
    @Column(name = "primary_trigger", nullable = false) String primaryTrigger;
    @Column(name = "known_reason") String knownReason;
    @Column(name = "source_barrier_report_id") UUID sourceBarrierReportId;
    @Column(name = "gap_days", nullable = false) int gapDays;
    @Column(name = "missed_window_count", nullable = false) int missedWindowCount;
    @Column(name = "selected_path") String selectedPath;
    @Column(name = "selected_at") Instant selectedAt;
    @Column(name = "target_planned_session_id") UUID targetPlannedSessionId;
    @Column(name = "return_attempt_id") UUID returnAttemptId;
    @Column(name = "return_started_at") Instant returnStartedAt;
    @Column(name = "return_local_date") LocalDate returnLocalDate;
    @Column(name = "first_session_execution_id") UUID firstSessionExecutionId;
    @Column(name = "first_session_outcome") String firstSessionOutcome;
    @Column(name = "resolved_at") Instant resolvedAt;
    @Version long version;

    protected RecoveryEpisode() { }

    RecoveryEpisode(UUID participant, String zone, LocalDate localDate, UUID revisionId, String trigger,
                    String reason, UUID barrierId, int gapDays, int missedWindows, Instant now) {
        id = UUID.randomUUID(); participantAccountId = participant; status = "OPEN";
        policyVersionCode = "RECOVERY_RETURN_V1"; openedAt = now; participantTimeZone = zone;
        detectedLocalDate = localDate; planRevisionIdAtOpening = revisionId; primaryTrigger = trigger;
        knownReason = reason; sourceBarrierReportId = barrierId; this.gapDays = gapDays; missedWindowCount = missedWindows;
    }

    boolean active() { return "OPEN".equals(status) || "RETURN_IN_PROGRESS".equals(status); }
    void select(String path, UUID sessionId, Instant now) { selectedPath = path; targetPlannedSessionId = sessionId; selectedAt = now; }
    void started(UUID attemptId, LocalDate localDate, Instant now) { status = "RETURN_IN_PROGRESS"; returnAttemptId = attemptId; returnLocalDate = localDate; returnStartedAt = now; }
    void resolved(UUID executionId, Instant now) { status = "RESOLVED"; firstSessionExecutionId = executionId; firstSessionOutcome = "COMPLETED"; resolvedAt = now; }
    void abandoned(Instant now) { status = "RESOLVED"; firstSessionOutcome = "ABANDONED"; resolvedAt = now; }
}
