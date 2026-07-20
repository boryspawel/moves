package com.motionecosystem.gamification;

import java.util.List;
import java.util.UUID;

import com.motionecosystem.gamification.GamificationService.LedgerView;
import com.motionecosystem.gamification.GamificationService.ProfileCommand;
import com.motionecosystem.gamification.GamificationService.ProfileView;
import com.motionecosystem.gamification.GamificationService.ProgressView;
import com.motionecosystem.gamification.GamificationService.QualificationView;
import com.motionecosystem.gamification.GamificationService.RankingRow;
import com.motionecosystem.gamification.GamificationService.ReversalCommand;
import com.motionecosystem.gamification.GamificationService.RuleCommand;
import com.motionecosystem.gamification.GamificationService.RuleView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "oidc")
class GamificationController {

    private final GamificationService gamification;

    GamificationController(GamificationService gamification) {
        this.gamification = gamification;
    }

    @PutMapping("/gamification/me/profile")
    @PreAuthorize("hasRole('PARTICIPANT')")
    @Operation(summary = "Enable, disable or configure the private gamification profile")
    ProfileView profile(@AuthenticationPrincipal Jwt jwt, @RequestBody ProfileCommand command) {
        return gamification.updateProfile(jwt.getSubject(), command);
    }

    @GetMapping("/gamification/me")
    @PreAuthorize("hasRole('PARTICIPANT')")
    @Operation(summary = "Return private points and a non-medical ledger view")
    ProgressView progress(@AuthenticationPrincipal Jwt jwt) {
        return gamification.progress(jwt.getSubject());
    }

    @PostMapping("/gamification/executions/{executionId}/qualifications")
    @PreAuthorize("hasRole('PARTICIPANT')")
    @Operation(summary = "Qualify a declared execution for points")
    QualificationView qualify(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable UUID executionId,
                              @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return gamification.qualify(jwt.getSubject(), executionId, idempotencyKey);
    }

    @GetMapping("/gamification/ranking")
    @Operation(summary = "Return the opt-in pseudonymous ranking")
    List<RankingRow> ranking() {
        return gamification.ranking();
    }

    @PostMapping("/admin/gamification/rules")
    @PreAuthorize("hasRole('GAMIFICATION_ADMIN')")
    @Operation(summary = "Publish an immutable point rule version")
    RuleView publishRule(@AuthenticationPrincipal Jwt jwt, @RequestBody RuleCommand command) {
        return gamification.publishRule(jwt.getSubject(), command);
    }

    @PostMapping("/admin/gamification/ledger/{entryId}/reversals")
    @PreAuthorize("hasRole('GAMIFICATION_ADMIN')")
    @Operation(summary = "Append a point reversal without changing ledger history")
    LedgerView reverse(@AuthenticationPrincipal Jwt jwt,
                       @PathVariable UUID entryId,
                       @RequestBody ReversalCommand command) {
        return gamification.reverse(jwt.getSubject(), entryId, command);
    }

    @PostMapping("/admin/gamification/ranking/rebuild")
    @PreAuthorize("hasRole('GAMIFICATION_ADMIN')")
    @Operation(summary = "Rebuild the ranking projection from the point ledger")
    List<RankingRow> rebuild(@AuthenticationPrincipal Jwt jwt) {
        return gamification.rebuildRanking(jwt.getSubject());
    }
}
