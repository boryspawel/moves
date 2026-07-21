package com.motionecosystem.safety;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ParticipantSafetyService {

    public static final String NON_DIAGNOSTIC_NOTICE =
            "User-reported input for specialist review; this is not a diagnosis.";

    private final CurrentAccountService accounts;
    private final ParticipantRestrictionRepository restrictions;
    private final ReadinessCheckInRepository checkIns;
    private final AuditRecorder audit;
    private final Clock clock;

    @Transactional
    public SafetyView checkIn(String subject, int painLevel, int readinessLevel, String painArea) {
        CurrentAccount account = accounts.requireActive(subject);
        if (painLevel < 0 || painLevel > 10 || readinessLevel < 1 || readinessLevel > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pain or readiness level is outside range");
        }
        String area = painArea == null || painArea.isBlank() ? null : painArea.trim();
        if (area != null && area.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pain area is too long");
        }
        ReadinessCheckIn saved = checkIns.save(
                new ReadinessCheckIn(account.id(), painLevel, readinessLevel, area, clock.instant()));
        audit.record(subject, "READINESS_CHECK_IN_RECORDED", "ReadinessCheckIn", saved.id);
        return view(account.id());
    }

    @Transactional
    public SafetyView current(String subject) {
        return view(accounts.requireActive(subject).id());
    }

    private SafetyView view(UUID accountId) {
        List<String> tags = restrictions.findByAccountIdOrderByContraindicationTag(accountId).stream()
                .map(item -> item.contraindicationTag)
                .toList();
        CheckInView latest = checkIns.findFirstByAccountIdOrderByRecordedAtDesc(accountId)
                .map(item -> new CheckInView(
                        item.id, item.painLevel, item.readinessLevel, item.painArea, item.recordedAt))
                .orElse(null);
        return new SafetyView(tags, latest, NON_DIAGNOSTIC_NOTICE);
    }

    public record CheckInView(UUID id, int painLevel, int readinessLevel, String painArea, Instant recordedAt) {
    }

    public record SafetyView(List<String> contraindicationTags, CheckInView latestCheckIn, String notice) {
    }
}
