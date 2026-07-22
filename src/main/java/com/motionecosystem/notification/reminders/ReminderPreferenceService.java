package com.motionecosystem.notification.reminders;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ReminderPreferenceService {
    private final CurrentAccountService accounts;
    private final ReminderPreferenceRepository preferences;
    private final AuditRecorder audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PreferenceView get(String subject) { return view(preferences.findById(participant(subject)).orElse(null)); }

    @Transactional
    public PreferenceView save(String subject, PreferenceCommand command) {
        UUID participant = participant(subject); validate(command);
        ReminderPreference preference = preferences.findById(participant).orElseGet(() -> new ReminderPreference(participant,
                command.timeZone(), command.preferredWindowStart(), command.preferredWindowEnd(), command.channel(),
                command.quietHoursStart(), command.quietHoursEnd(), command.muted(), command.maxMessagesPerWeek(),
                command.remindersEnabled(), command.gentleReturnConsent(), clock.instant()));
        preference.replace(command.timeZone(), command.preferredWindowStart(), command.preferredWindowEnd(), command.channel(),
                command.quietHoursStart(), command.quietHoursEnd(), command.muted(), command.maxMessagesPerWeek(),
                command.remindersEnabled(), command.gentleReturnConsent(), clock.instant());
        preferences.save(preference); audit.record(subject, "REMINDER_PREFERENCES_UPDATED", "ReminderPreference", participant);
        return view(preference);
    }
    private UUID participant(String subject) {
        var account = accounts.requireActive(subject);
        if (!account.hasProfile(ProfileType.PARTICIPANT)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "participant profile is required");
        return account.id();
    }
    private static void validate(PreferenceCommand value) {
        if (value == null || value.timeZone() == null || value.preferredWindowStart() == null || value.preferredWindowEnd() == null
                || value.channel() == null) bad("timeZone, preferred window and channel are required");
        try { ZoneId.of(value.timeZone()); } catch (RuntimeException e) { bad("timeZone is invalid"); }
        if (!"IN_APP".equals(value.channel()) || value.maxMessagesPerWeek() < 1 || value.maxMessagesPerWeek() > 7) bad("reminder preferences are invalid");
    }
    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static PreferenceView view(ReminderPreference p) {
        return p == null ? null : new PreferenceView(p.timeZone, p.preferredWindowStart, p.preferredWindowEnd, p.channel,
                p.quietHoursStart, p.quietHoursEnd, p.muted, p.maxMessagesPerWeek, p.remindersEnabled, p.gentleReturnConsent);
    }
    public record PreferenceCommand(String timeZone, LocalTime preferredWindowStart, LocalTime preferredWindowEnd, String channel,
                                    LocalTime quietHoursStart, LocalTime quietHoursEnd, boolean muted, int maxMessagesPerWeek,
                                    boolean remindersEnabled, boolean gentleReturnConsent) { }
    public record PreferenceView(String timeZone, LocalTime preferredWindowStart, LocalTime preferredWindowEnd, String channel,
                                 LocalTime quietHoursStart, LocalTime quietHoursEnd, boolean muted, int maxMessagesPerWeek,
                                 boolean remindersEnabled, boolean gentleReturnConsent) { }
}
