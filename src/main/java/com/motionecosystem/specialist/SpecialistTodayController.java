package com.motionecosystem.specialist;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/specialist/today")
@SecurityRequirement(name = "oidc")
@RequiredArgsConstructor
public class SpecialistTodayController {
    private final SpecialistTodayService today;
    @GetMapping @PreAuthorize("hasRole('SPECIALIST')")
    @Operation(summary = "Get the bounded operational Today view in the specialist's persisted time zone")
    SpecialistTodayService.TodayView getToday(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return today.today(jwt.getSubject(), date);
    }
}
