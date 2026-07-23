package com.motionecosystem.exercisecatalog;

import java.util.List;
import java.util.UUID;
import com.motionecosystem.exercisecatalog.api.PublishExerciseVersion;
import com.motionecosystem.exercisecatalog.api.ReviewExerciseVersion;

import com.motionecosystem.exercisecatalog.CatalogService.ContributionCommand;
import com.motionecosystem.exercisecatalog.CatalogService.EvidenceCommand;
import com.motionecosystem.exercisecatalog.CatalogService.LoadCharacteristicCommand;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
    private final ExerciseEditorialWorkflowService workflow;

    ExerciseCatalogAdminController(CatalogService catalog, ExerciseEditorialWorkflowService workflow) {
        this.catalog = catalog;
        this.workflow = workflow;
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

    @PutMapping("/versions/{versionId}/editorial")
    CatalogService.VersionView updateEditorialDraft(@AuthenticationPrincipal Jwt jwt,
                                                    @PathVariable UUID versionId,
                                                    @RequestBody CatalogService.DraftUpdateCommand request) {
        return catalog.updateEditorialDraft(jwt.getSubject(), versionId, request);
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
    ReviewExerciseVersion.ReviewResult approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID versionId) {
        ReviewExerciseVersion.ReviewResult result = null;
        for (String area : List.of("CONTENT", "TECHNIQUE", "ANATOMY_EXPOSURE", "LICENSE")) {
            result = workflow.review(versionId, jwt.getSubject(),
                    new ReviewExerciseVersion.ReviewCommand(area, "APPROVED", "Legacy aggregate approval", null));
        }
        return result;
    }

    @PostMapping("/versions/{versionId}/publish")
    PublishExerciseVersion.PublicationResult publish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID versionId,
                                                     @Valid @RequestBody PublishRequest request) {
        return workflow.publish(versionId, jwt.getSubject(), request.expectedVersion());
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

    record PublishRequest(@NotNull @PositiveOrZero Long expectedVersion) {
    }
}
