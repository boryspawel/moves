package com.motionecosystem.adherence;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/participant/recovery-episodes")
@SecurityRequirement(name = "oidc")
@RequiredArgsConstructor
class RecoveryEpisodeController {
    private final RecoveryEpisodeService recovery;
    @GetMapping("/current") @PreAuthorize("hasRole('PARTICIPANT')")
    RecoveryEpisodeService.RecoveryView current(@AuthenticationPrincipal Jwt jwt) { return recovery.current(jwt.getSubject()); }
    @PostMapping("/{episodeId}/choices") @PreAuthorize("hasRole('PARTICIPANT')")
    RecoveryEpisodeService.RecoveryView choose(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID episodeId,
            @RequestHeader("Idempotency-Key") String key, @RequestBody RecoveryChoiceCommand command) {
        return recovery.choose(jwt.getSubject(), episodeId, command.offerId(), command.aggregateVersion(), command.path(), key);
    }
    record RecoveryChoiceCommand(UUID offerId, long aggregateVersion, String path) { }
}
