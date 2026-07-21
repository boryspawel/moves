package com.motionecosystem.anatomyreference.api;

import java.util.List;
import java.util.UUID;

import com.motionecosystem.anatomyreference.application.AnatomyReferenceService;
import com.motionecosystem.anatomyreference.application.AnatomyReferenceService.AddRelationCommand;
import com.motionecosystem.anatomyreference.application.AnatomyReferenceService.CreateStructureCommand;
import com.motionecosystem.anatomyreference.application.AnatomyReferenceService.RelationSnapshot;
import com.motionecosystem.anatomyreference.domain.AnatomicalStructureType;
import com.motionecosystem.anatomyreference.domain.RelationType;
import com.motionecosystem.anatomyreference.domain.SidePolicy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/anatomical-structures")
@SecurityRequirement(name = "oidc")
@PreAuthorize("hasRole('CONTENT_ADMIN')")
class AnatomyReferenceAdminController {

    private final AnatomyReferenceService anatomy;
    private final AnatomyReferenceQueryPort queries;

    AnatomyReferenceAdminController(AnatomyReferenceService anatomy, AnatomyReferenceQueryPort queries) {
        this.anatomy = anatomy;
        this.queries = queries;
    }

    @PostMapping
    @Operation(summary = "Create a draft anatomical structure")
    AnatomyReferenceQueryPort.AnatomicalStructureSnapshot create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateStructureRequest request) {
        return anatomy.createDraft(jwt.getSubject(), new CreateStructureCommand(request.code(), request.type(),
                request.displayName(), request.sidePolicy(), request.externalOntology(),
                request.externalOntologyId(), request.taxonomyVersion()));
    }

    @PostMapping("/{structureId}/publish")
    @Operation(summary = "Publish an immutable anatomical structure")
    AnatomyReferenceQueryPort.AnatomicalStructureSnapshot publish(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID structureId) {
        return anatomy.publish(jwt.getSubject(), structureId);
    }

    @PostMapping("/{structureId}/withdraw")
    @Operation(summary = "Withdraw a published anatomical structure")
    AnatomyReferenceQueryPort.AnatomicalStructureSnapshot withdraw(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID structureId) {
        return anatomy.withdraw(jwt.getSubject(), structureId);
    }

    @PostMapping("/relations")
    @Operation(summary = "Add an acyclic parent-child relation between draft structures")
    RelationSnapshot addRelation(@AuthenticationPrincipal Jwt jwt,
                                 @Valid @RequestBody AddRelationRequest request) {
        return anatomy.addRelation(jwt.getSubject(),
                new AddRelationCommand(request.parentId(), request.childId(), request.relationType()));
    }

    @GetMapping("/{structureId}")
    AnatomyReferenceQueryPort.AnatomicalStructureSnapshot get(@PathVariable UUID structureId) {
        return queries.findStructure(structureId)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "anatomical structure not found"));
    }

    @GetMapping("/{structureId}/ancestors")
    List<AnatomyReferenceQueryPort.AncestorPath> ancestors(@PathVariable UUID structureId) {
        return queries.ancestorPaths(structureId);
    }

    record CreateStructureRequest(
            @NotBlank @Size(max = 80) String code,
            @NotNull AnatomicalStructureType type,
            @NotBlank @Size(max = 160) String displayName,
            @NotNull SidePolicy sidePolicy,
            @Size(max = 120) String externalOntology,
            @Size(max = 200) String externalOntologyId,
            @Min(1) int taxonomyVersion) {
    }

    record AddRelationRequest(@NotNull UUID parentId, @NotNull UUID childId,
                              @NotNull RelationType relationType) {
    }
}
