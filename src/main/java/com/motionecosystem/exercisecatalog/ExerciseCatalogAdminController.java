package com.motionecosystem.exercisecatalog;

import java.util.List;
import java.util.UUID;

import com.motionecosystem.exercisecatalog.CatalogService.ContributionCommand;
import com.motionecosystem.exercisecatalog.CatalogService.EvidenceCommand;
import com.motionecosystem.exercisecatalog.CatalogService.LoadCharacteristicCommand;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/exercises")
@SecurityRequirement(name = "oidc")
@PreAuthorize("hasRole('CONTENT_ADMIN')")
class ExerciseCatalogAdminController {

    private final CatalogService catalog;

    ExerciseCatalogAdminController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @PostMapping
    CatalogService.VersionView create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateRequest request) {
        return catalog.create(jwt.getSubject(), request.canonicalName(), request.version());
    }

    @PostMapping("/{exerciseId}/versions")
    CatalogService.VersionView createVersion(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable UUID exerciseId,
                                             @RequestBody CatalogService.VersionCommand request) {
        return catalog.createNextVersion(jwt.getSubject(), exerciseId, request);
    }

    @PutMapping("/versions/{versionId}")
    CatalogService.VersionView update(@AuthenticationPrincipal Jwt jwt,
                                      @PathVariable UUID versionId,
                                      @RequestBody CatalogService.VersionCommand request) {
        return catalog.updateDraft(jwt.getSubject(), versionId, request);
    }

    @PutMapping("/versions/{versionId}/load-characteristics")
    CatalogService.EditorView replaceLoadCharacteristics(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID versionId,
            @RequestBody List<LoadCharacteristicCommand> request) {
        return catalog.replaceLoadCharacteristics(jwt.getSubject(), versionId, request);
    }

    @PostMapping("/versions/{versionId}/evidence")
    CatalogService.EvidenceView addEvidence(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable UUID versionId,
                                            @RequestBody EvidenceCommand request) {
        return catalog.addEvidence(jwt.getSubject(), versionId, request);
    }

    @PostMapping("/versions/{versionId}/contributions")
    CatalogService.ContributionView addContribution(@AuthenticationPrincipal Jwt jwt,
                                                    @PathVariable UUID versionId,
                                                    @RequestBody ContributionCommand request) {
        return catalog.addContribution(jwt.getSubject(), versionId, request);
    }

    @PostMapping("/versions/{versionId}/submit-review")
    CatalogService.VersionView submitReview(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable UUID versionId) {
        return catalog.submitForReview(jwt.getSubject(), versionId);
    }

    @PostMapping("/versions/{versionId}/request-changes")
    CatalogService.VersionView requestChanges(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable UUID versionId) {
        return catalog.requestChanges(jwt.getSubject(), versionId);
    }

    @PostMapping("/versions/{versionId}/approve")
    CatalogService.VersionView approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID versionId) {
        return catalog.approve(jwt.getSubject(), versionId);
    }

    @PostMapping("/versions/{versionId}/publish")
    CatalogService.VersionView publish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID versionId) {
        return catalog.publish(jwt.getSubject(), versionId);
    }

    @PostMapping("/versions/{versionId}/withdraw")
    CatalogService.VersionView withdraw(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID versionId) {
        return catalog.withdraw(jwt.getSubject(), versionId);
    }

    @GetMapping("/{exerciseId}/versions")
    List<CatalogService.VersionView> versions(@PathVariable UUID exerciseId) {
        return catalog.allVersions(exerciseId);
    }

    @GetMapping("/versions/{versionId}/editor")
    CatalogService.EditorView editor(@PathVariable UUID versionId) {
        return catalog.editor(versionId);
    }

    @GetMapping("/legacy/contraindications")
    List<CatalogService.LegacyContraindicationReportItem> legacyContraindications() {
        return catalog.legacyContraindicationReport();
    }

    record CreateRequest(String canonicalName, CatalogService.VersionCommand version) {
    }
}
