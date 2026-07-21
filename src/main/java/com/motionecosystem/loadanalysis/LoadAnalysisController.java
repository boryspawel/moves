package com.motionecosystem.loadanalysis;

import java.util.UUID;

import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort.LoadCalculationVersion;
import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort.LoadProfile;
import com.motionecosystem.specialist.SpecialistRelationshipService;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/training-plans/revisions")
@RequiredArgsConstructor
class LoadAnalysisController {
    private final CurrentAccountService accounts;
    private final SpecialistRelationshipService relationships;
    private final PlanRevisionQueryPort revisions;
    private final PlannedLoadService loads;

    @GetMapping("/{revisionId}/load-preview")
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'SPECIALIST')")
    LoadProfile preview(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID revisionId,
                        @RequestParam(defaultValue = "LOAD_V1") String algorithmVersion,
                        @RequestParam(defaultValue = "DEFAULT_V1") String configurationVersion) {
        var actor = accounts.requireActive(jwt.getSubject());
        var revision = revisions.findRevision(revisionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "revision not found"));
        if (actor.profileType() == ProfileType.PARTICIPANT) {
            if (!actor.id().equals(revision.participantAccountId()))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "revision belongs to another participant");
        } else if (actor.profileType() == ProfileType.SPECIALIST) {
            relationships.requireActive(actor.id(), revision.participantAccountId());
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "profile cannot preview planned load");
        }
        return loads.calculate(revision, new LoadCalculationVersion(algorithmVersion, configurationVersion));
    }
}
