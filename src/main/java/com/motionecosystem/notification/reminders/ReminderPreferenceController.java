package com.motionecosystem.notification.reminders;

import com.motionecosystem.notification.reminders.ReminderPreferenceService.PreferenceCommand;
import com.motionecosystem.notification.reminders.ReminderPreferenceService.PreferenceView;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/participant/reminder-preferences")
@SecurityRequirement(name = "oidc")
@RequiredArgsConstructor
class ReminderPreferenceController {
    private final ReminderPreferenceService preferences;
    @GetMapping @PreAuthorize("hasRole('PARTICIPANT')") PreferenceView get(@AuthenticationPrincipal Jwt jwt) { return preferences.get(jwt.getSubject()); }
    @PutMapping @PreAuthorize("hasRole('PARTICIPANT')") PreferenceView save(@AuthenticationPrincipal Jwt jwt, @RequestBody PreferenceCommand command) { return preferences.save(jwt.getSubject(), command); }
}
