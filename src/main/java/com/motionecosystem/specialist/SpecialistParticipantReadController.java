package com.motionecosystem.specialist;

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
@RequestMapping("/api/v1/specialist/participants/{participantId}")
@SecurityRequirement(name = "oidc")
@RequiredArgsConstructor
public class SpecialistParticipantReadController {
    private final SpecialistParticipantReadService reads;

    @GetMapping("/workspace")
    @PreAuthorize("hasRole('SPECIALIST')")
    @Operation(summary = "Get the authorized, bounded specialist participant workspace")
    SpecialistParticipantReadService.SpecialistParticipantWorkspaceView workspace(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID participantId) {
        return reads.workspace(jwt.getSubject(), participantId);
    }

    @GetMapping("/timeline")
    @PreAuthorize("hasRole('SPECIALIST')")
    @Operation(summary = "Get the authorized, cursor-paginated specialist participant timeline")
    SpecialistParticipantReadService.ParticipantTimelineView timeline(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID participantId, @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to, @RequestParam(required = false) String types,
            @RequestParam(required = false) SpecialistParticipantReadService.Granularity granularity,
            @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        return reads.timeline(jwt.getSubject(), participantId,
                new SpecialistParticipantReadService.TimelineQuery(from, to, parseTypes(types), granularity, cursor, limit));
    }

    private static Set<SpecialistParticipantReadService.TimelineType> parseTypes(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isEmpty())
                    .map(String::toUpperCase).map(SpecialistParticipantReadService.TimelineType::valueOf)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "types contains an unsupported value");
        }
    }
}
