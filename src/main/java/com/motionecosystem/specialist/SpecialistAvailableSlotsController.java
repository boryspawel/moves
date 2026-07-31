package com.motionecosystem.specialist;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/specialist/available-slots")
@SecurityRequirement(name = "oidc")
@RequiredArgsConstructor
class SpecialistAvailableSlotsController {
    private final SpecialistAvailableSlotsService slots;

    @GetMapping
    @PreAuthorize("hasRole('SPECIALIST')")
    @Operation(summary = "List available specialist appointment slots")
    SpecialistAvailableSlotsService.AvailableSlotsView list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam int durationMinutes) {
        return slots.list(jwt.getSubject(), date, durationMinutes);
    }
}
