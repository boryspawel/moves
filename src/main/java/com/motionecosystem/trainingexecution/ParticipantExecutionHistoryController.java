package com.motionecosystem.trainingexecution;

import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.participant.api.ParticipantClientPort;
import com.motionecosystem.trainingexecution.api.ParticipantExecutionHistoryQueryPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/participant/execution-history")
@SecurityRequirement(name = "oidc")
@RequiredArgsConstructor
class ParticipantExecutionHistoryController {
    private static final int DEFAULT_LIMIT = 50;
    private final CurrentAccountService accounts;
    private final ParticipantClientPort participants;
    private final ParticipantExecutionHistoryQueryPort history;

    @GetMapping
    @PreAuthorize("hasRole('PARTICIPANT')")
    @Operation(operationId = "getOwnExecutionHistory")
    List<ParticipantExecutionHistoryQueryPort.ExecutionStart> timeline(@AuthenticationPrincipal Jwt jwt,
            @RequestParam Instant from, @RequestParam Instant to, @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        if (!to.isAfter(from)) throw bad("to must be after from");
        int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;
        if (effectiveLimit < 1 || effectiveLimit > 100) throw bad("limit must be between 1 and 100");
        var account = accounts.requireActive(jwt.getSubject());
        if (!account.hasProfile(ProfileType.PARTICIPANT)) throw forbidden("participant profile is required");
        UUID participantId = participants.findParticipantIdByPrincipalAccountId(account.id())
                .orElseThrow(() -> forbidden("an active participant access link is required"));
        return history.timeline(participantId, from, to, parseCursor(cursor), effectiveLimit);
    }

    private static ParticipantExecutionHistoryQueryPort.SeekCursor parseCursor(String value) {
        if (value == null || value.isBlank()) return null;
        String[] parts = value.split("\\|", -1);
        if (parts.length != 3) throw bad("cursor is invalid");
        try { return new ParticipantExecutionHistoryQueryPort.SeekCursor(Instant.parse(parts[0]), Instant.parse(parts[1]), UUID.fromString(parts[2])); }
        catch (RuntimeException invalid) { throw bad("cursor is invalid"); }
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException forbidden(String message) { return new ResponseStatusException(HttpStatus.FORBIDDEN, message); }
}
