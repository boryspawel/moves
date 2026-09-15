package com.motionecosystem.adherence;

import com.motionecosystem.adherence.api.AdherenceSummary;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.participant.api.ParticipantClientPort;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/participant/adherence-summary")
@RequiredArgsConstructor
class AdherenceSummaryController {
    private final CurrentAccountService accounts;
    private final ParticipantClientPort participants;
    private final AdherenceSummaryService summaries;

    @GetMapping
    @PreAuthorize("hasRole('PARTICIPANT')")
    AdherenceSummary summary(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var account = accounts.requireActive(jwt.getSubject());
        if (!account.hasProfile(ProfileType.PARTICIPANT)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "participant profile is required");
        var participant = participants.findParticipantIdByPrincipalAccountId(account.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "an active participant access link is required"));
        return summaries.summarize(participant, from, to);
    }
}
