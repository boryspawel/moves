package com.motionecosystem.notification.reminders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "reminder_preference", schema = "notification")
class ReminderPreference {
    @Id @Column(name = "participant_account_id") UUID participantAccountId;
    @Column(name = "time_zone") String timeZone;
    @Column(name = "preferred_window_start") LocalTime preferredWindowStart;
    @Column(name = "preferred_window_end") LocalTime preferredWindowEnd;
    String channel;
    @Column(name = "quiet_hours_start") LocalTime quietHoursStart;
    @Column(name = "quiet_hours_end") LocalTime quietHoursEnd;
    boolean muted;
    @Column(name = "max_messages_per_week") int maxMessagesPerWeek;
    @Column(name = "reminders_enabled") boolean remindersEnabled;
    @Column(name = "gentle_return_consent") boolean gentleReturnConsent;
    @Column(name = "updated_at") Instant updatedAt;
    protected ReminderPreference() { }
    ReminderPreference(UUID participant, String zone, LocalTime preferredStart, LocalTime preferredEnd, String channel,
                       LocalTime quietStart, LocalTime quietEnd, boolean muted, int maximum, boolean enabled,
                       boolean gentleReturnConsent, Instant updatedAt) {
        participantAccountId = participant; timeZone = zone; preferredWindowStart = preferredStart; preferredWindowEnd = preferredEnd;
        this.channel = channel; quietHoursStart = quietStart; quietHoursEnd = quietEnd; this.muted = muted;
        maxMessagesPerWeek = maximum; remindersEnabled = enabled; this.gentleReturnConsent = gentleReturnConsent; this.updatedAt = updatedAt;
    }
    void replace(String zone, LocalTime preferredStart, LocalTime preferredEnd, String channel, LocalTime quietStart,
                 LocalTime quietEnd, boolean muted, int maximum, boolean enabled, boolean returnConsent, Instant now) {
        timeZone = zone; preferredWindowStart = preferredStart; preferredWindowEnd = preferredEnd; this.channel = channel;
        quietHoursStart = quietStart; quietHoursEnd = quietEnd; this.muted = muted; maxMessagesPerWeek = maximum;
        remindersEnabled = enabled; gentleReturnConsent = returnConsent; updatedAt = now;
    }
}
