package com.motionecosystem.adherence;

import com.motionecosystem.adherence.TodayAgendaService.TodayAgendaView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/participant/today")
@SecurityRequirement(name = "oidc")
@RequiredArgsConstructor
class TodayAgendaController {

    private final TodayAgendaService agenda;

    @GetMapping
    @PreAuthorize("hasRole('PARTICIPANT')")
    @Operation(summary = "Get the signed-in participant's daily training agenda")
    TodayAgendaView today(@AuthenticationPrincipal Jwt jwt) {
        return agenda.today(jwt.getSubject());
    }
}
