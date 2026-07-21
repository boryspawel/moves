package com.motionecosystem.consent;

import java.util.UUID;
import com.motionecosystem.consent.ConsentGrantService.GrantCommand;
import com.motionecosystem.consent.ConsentGrantService.GrantView;
import com.motionecosystem.consent.ConsentGrantService.TemplateView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/consent")
@RequiredArgsConstructor
class ConsentController {

    private final ConsentGrantService service;

    @PostMapping("/templates")
    @PreAuthorize("hasRole('ADMIN')")
    TemplateView template(@RequestBody TemplateCommand command) {
        return service.publishTemplate(
                command.code(),
                command.versionNumber(),
                command.contentReference(),
                command.legalBasis());
    }

    @PostMapping("/grants")
    @PreAuthorize("hasRole('PARTICIPANT')")
    GrantView grant(@AuthenticationPrincipal Jwt jwt, @RequestBody GrantCommand command) {
        return service.grant(jwt.getSubject(), command);
    }

    @PostMapping("/grants/{grantId}/revoke")
    @PreAuthorize("hasRole('PARTICIPANT')")
    GrantView revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID grantId) {
        return service.revoke(jwt.getSubject(), grantId);
    }

    record TemplateCommand(
            String code,
            int versionNumber,
            String contentReference,
            String legalBasis) {
    }
}
