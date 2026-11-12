package com.motionecosystem.application.workspace;

import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ProfessionalRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/specialist/participants/{participantId}/history")
@SecurityRequirement(name = "oidc")
@RequiredArgsConstructor
public class SpecialistParticipantHistoryController {
    private final SpecialistParticipantHistoryService history;

    @GetMapping
    @PreAuthorize("hasRole('SPECIALIST')")
    @Operation(operationId = "getSpecialistParticipantHistory", summary = "Get authorized, cursor-paginated participant encounter history")
    SpecialistParticipantHistoryService.HistoryPage get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId,
            @RequestParam ProfessionalRole actingContext, @RequestParam Instant from, @RequestParam Instant to,
            @RequestParam(required = false) String types, @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return history.history(jwt.getSubject(), participantId, new ActingContext(actingContext),
                new SpecialistParticipantHistoryService.HistoryQuery(from, to, parseTypes(types), limit, cursor));
    }

    @GetMapping("/resolve")
    @PreAuthorize("hasRole('SPECIALIST')")
    @Operation(operationId = "resolveSpecialistParticipantHistory", summary = "Resolve an authorized participant history deep link")
    SpecialistParticipantHistoryService.HistoryResolution resolve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId,
            @RequestParam ProfessionalRole actingContext, @RequestParam String eventId) {
        return history.resolve(jwt.getSubject(), participantId, new ActingContext(actingContext), eventId);
    }

    private static Set<SpecialistParticipantHistoryService.FilterKind> parseTypes(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isEmpty())
                    .map(String::toUpperCase).map(SpecialistParticipantHistoryService.FilterKind::valueOf)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "types contains an unsupported value");
        }
    }
}
