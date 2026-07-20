package com.motionecosystem.safety;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ParticipantSafetyService {

    public static final String NON_DIAGNOSTIC_NOTICE =
            "User-reported input for specialist review; this is not a diagnosis.";

    private final CurrentAccountService accounts;
    private final ParticipantRestrictionRepository restrictions;
    private final ReadinessCheckInRepository checkIns;
    private final AuditRecorder audit;
    private final Clock clock;

    ParticipantSafetyService(CurrentAccountService accounts,
                             ParticipantRestrictionRepository restrictions,
                             ReadinessCheckInRepository checkIns,
                             AuditRecorder audit,
                             Clock clock) {
        this.accounts = accounts;
        this.restrictions = restrictions;
        this.checkIns = checkIns;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public SafetyView replaceRestrictions(String subject, Collection<String> requested) {
        CurrentAccount account = accounts.requireActive(subject);
        Set<String> tags = normalizeTags(requested);
        restrictions.deleteByAccountId(account.id());
        restrictions.flush();
        Instant now = clock.instant();
        restrictions.saveAll(tags.stream()
                .map(tag -> new ParticipantRestriction(account.id(), tag, now))
                .toList());
        audit.record(subject, "PARTICIPANT_RESTRICTIONS_REPLACED", "PrincipalAccount", account.id());
        return view(account.id());
    }

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

    private static Set<String> normalizeTags(Collection<String> requested) {
        if (requested == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "restriction tags are required");
        }
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        requested.forEach(value -> {
            String tag = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            if (tag.isEmpty() || tag.length() > 80) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "restriction tag is invalid");
            }
            tags.add(tag);
        });
        return Set.copyOf(tags);
    }

    public record CheckInView(UUID id, int painLevel, int readinessLevel, String painArea, Instant recordedAt) {
    }

    public record SafetyView(List<String> contraindicationTags, CheckInView latestCheckIn, String notice) {
    }
}
