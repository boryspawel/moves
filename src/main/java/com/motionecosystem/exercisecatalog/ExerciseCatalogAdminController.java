package com.motionecosystem.exercisecatalog;

import java.util.List;
import java.util.UUID;
import com.motionecosystem.exercisecatalog.api.PublishExerciseVersion;
import com.motionecosystem.exercisecatalog.api.ReviewExerciseVersion;

import com.motionecosystem.exercisecatalog.CatalogService.ContributionCommand;
import com.motionecosystem.exercisecatalog.CatalogService.EvidenceCommand;
import com.motionecosystem.exercisecatalog.CatalogService.LoadCharacteristicCommand;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping
    @Operation(operationId = "listEditorialExercises")
    CatalogService.EditorialCatalogPage list(@RequestParam(required = false) String query,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return catalog.editorialList(query, page, size);
    }

    @PostMapping
    @Operation(operationId = "createEditorialExercise")
    CatalogService.ExerciseEditorialVersionView create(@AuthenticationPrincipal Jwt jwt, @RequestBody CatalogCreateRequest request) {
        return catalog.create(jwt.getSubject(), request.canonicalName(), request.version());
    }

    @PostMapping("/{exerciseId}/versions")
    @Operation(operationId = "createEditorialExerciseVersion")
    CatalogService.ExerciseEditorialVersionView createVersion(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable UUID exerciseId,
                                             @RequestBody CatalogService.VersionCommand request) {
        return catalog.createNextVersion(jwt.getSubject(), exerciseId, request);
    }

    @PutMapping("/versions/{versionId}")
    @Operation(operationId = "updateEditorialExerciseDraft")
    CatalogService.ExerciseEditorialVersionView update(@AuthenticationPrincipal Jwt jwt,
                                      @PathVariable UUID versionId,
                                      @RequestBody CatalogService.VersionCommand request) {
        return catalog.updateDraft(jwt.getSubject(), versionId, request);
    }

    @PutMapping("/versions/{versionId}/editorial")
    @Operation(operationId = "updateEditorialExerciseContent")
    CatalogService.ExerciseEditorialVersionView updateEditorialDraft(@AuthenticationPrincipal Jwt jwt,
                                                    @PathVariable UUID versionId,
                                                    @RequestBody CatalogService.DraftUpdateCommand request) {
        return catalog.updateEditorialDraft(jwt.getSubject(), versionId, request);
    }

    @PutMapping("/versions/{versionId}/load-characteristics")
    @Operation(operationId = "replaceEditorialLoadCharacteristics")
    CatalogService.ExerciseEditorialEditorView replaceLoadCharacteristics(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID versionId,
            @RequestBody List<LoadCharacteristicCommand> request) {
        return catalog.replaceLoadCharacteristics(jwt.getSubject(), versionId, request);
    }

    @PostMapping("/versions/{versionId}/evidence")
    @Operation(operationId = "addEditorialEvidence")
    CatalogService.EvidenceView addEvidence(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable UUID versionId,
                                            @RequestBody EvidenceCommand request) {
        return catalog.addEvidence(jwt.getSubject(), versionId, request);
    }

    @PostMapping("/versions/{versionId}/contributions")
    @Operation(operationId = "addEditorialContribution")
    CatalogService.ContributionView addContribution(@AuthenticationPrincipal Jwt jwt,
                                                    @PathVariable UUID versionId,
                                                    @RequestBody ContributionCommand request) {
        return catalog.addContribution(jwt.getSubject(), versionId, request);
    }

    @PostMapping("/versions/{versionId}/submit-review")
    @Operation(operationId = "submitEditorialExerciseForReview")
    CatalogService.ExerciseEditorialVersionView submitReview(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable UUID versionId) {
        return catalog.submitForReview(jwt.getSubject(), versionId);
    }

    @PostMapping("/versions/{versionId}/request-changes")
    @Operation(operationId = "requestEditorialExerciseChanges")
    CatalogService.ExerciseEditorialVersionView requestChanges(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable UUID versionId) {
        return catalog.requestChanges(jwt.getSubject(), versionId);
    }

    @PostMapping("/versions/{versionId}/approve")
    @Operation(operationId = "approveEditorialExercise")
    ReviewExerciseVersion.ReviewResult approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID versionId) {
        ReviewExerciseVersion.ReviewResult result = null;
        for (String area : List.of("CONTENT", "TECHNIQUE", "ANATOMY_EXPOSURE", "LICENSE")) {
            result = workflow.review(versionId, jwt.getSubject(),
                    new ReviewExerciseVersion.ReviewCommand(area, "APPROVED", "Legacy aggregate approval", null));
        }
        return result;
    }

    @PostMapping("/versions/{versionId}/publish")
    @Operation(operationId = "publishEditorialExercise")
    PublishExerciseVersion.PublicationResult publish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID versionId,
                                                     @Valid @RequestBody PublishRequest request) {
        return workflow.publish(versionId, jwt.getSubject(), request.expectedVersion());
    }

    @PostMapping("/versions/{versionId}/withdraw")
    @Operation(operationId = "withdrawEditorialExercise")
    CatalogService.ExerciseEditorialVersionView withdraw(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID versionId) {
        return catalog.withdraw(jwt.getSubject(), versionId);
    }

    @GetMapping("/{exerciseId}/versions")
    @Operation(operationId = "listEditorialExerciseVersions")
    List<CatalogService.ExerciseEditorialVersionView> versions(@PathVariable UUID exerciseId) {
        return catalog.allVersions(exerciseId);
    }

    @GetMapping("/versions/{versionId}/editor")
    @Operation(operationId = "getEditorialExerciseEditor")
    CatalogService.ExerciseEditorialEditorView editor(@PathVariable UUID versionId) {
        return catalog.editor(versionId);
    }

    @GetMapping("/versions/{versionId}/capabilities")
    @Operation(operationId = "getEditorialExerciseCapabilities")
    CatalogService.EditorialCapabilities capabilities(@PathVariable UUID versionId) {
        return catalog.editorialCapabilities(versionId);
    }

    @GetMapping("/legacy/contraindications")
    @Operation(operationId = "listLegacyEditorialContraindications")
    List<CatalogService.LegacyContraindicationReportItem> legacyContraindications() {
        return catalog.legacyContraindicationReport();
    }

    /** Distinct schema name avoids a TypeScript generator collision with operation CreateRequest. */
    record CatalogCreateRequest(String canonicalName, CatalogService.VersionCommand version) {
    }

    record PublishRequest(@NotNull @PositiveOrZero Long expectedVersion) {
    }
}
