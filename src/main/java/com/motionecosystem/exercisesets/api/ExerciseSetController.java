package com.motionecosystem.exercisesets.api;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.motionecosystem.exercisesets.api.ExerciseSetDtos.*;
import com.motionecosystem.exercisesets.ports.ExerciseSetCommandPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/specialist/exercise-sets")
@SecurityRequirement(name="oidc")
@PreAuthorize("hasRole('SPECIALIST')")
@RequiredArgsConstructor
public class ExerciseSetController {
    private final ExerciseSetCommandPort service;
    @PostMapping @Operation(summary="Create an independent exercise set and its first draft")
    public ResponseEntity<SetView> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody(required=false) CreateRequest request) { var result=service.create(jwt.getSubject()); return ResponseEntity.created(URI.create("/api/v1/specialist/exercise-sets/"+result.id())).body(result); }
    @GetMapping public List<SetView> list(@AuthenticationPrincipal Jwt jwt) { return service.list(jwt.getSubject()); }
    @GetMapping("/{setId}") public SetView get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID setId){return service.get(jwt.getSubject(),setId);}
    @GetMapping("/{setId}/versions") public List<VersionSummary> versions(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID setId){return service.listVersions(jwt.getSubject(),setId);}
    @GetMapping("/{setId}/versions/{versionId}") public VersionView version(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID setId,@PathVariable UUID versionId){return service.version(jwt.getSubject(),setId,versionId);}
    @GetMapping("/{setId}/versions/draft") public VersionView draft(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID setId){return service.currentDraft(jwt.getSubject(),setId);}
    @GetMapping("/{setId}/versions/published/latest") public VersionView latest(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID setId){return service.latestPublished(jwt.getSubject(),setId);}
    @GetMapping("/{setId}/versions/{versionId}/analysis") @Operation(summary="Analyze a draft on demand or return the immutable published analysis") public AnalysisView analysis(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID setId,@PathVariable UUID versionId){return service.analysis(jwt.getSubject(),setId,versionId);}
    @GetMapping("/{setId}/versions/{versionId}/anatomy") @Operation(summary="Analyze draft anatomy from exact published exercise snapshots or return immutable published anatomy") public AnatomyAnalysisView anatomy(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID setId,@PathVariable UUID versionId){return service.anatomy(jwt.getSubject(),setId,versionId);}
    @PutMapping("/{setId}/versions/{versionId}") public VersionView metadata(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID setId,@PathVariable UUID versionId,@Valid @RequestBody MetadataRequest request){return service.updateMetadata(jwt.getSubject(),setId,versionId,request);}
    @PostMapping("/{setId}/versions/{versionId}/items") public VersionView add(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID setId,@PathVariable UUID versionId,@Valid @RequestBody ItemRequest request){return service.addItem(jwt.getSubject(),setId,versionId,request);}
    @PutMapping("/{setId}/versions/{versionId}/items/{itemId}") public VersionView update(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID setId,@PathVariable UUID versionId,@PathVariable UUID itemId,@Valid @RequestBody ItemRequest request){return service.updateItem(jwt.getSubject(),setId,versionId,itemId,request);}
    @PostMapping("/{setId}/versions/{versionId}/items/move") public VersionView move(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID setId,@PathVariable UUID versionId,@Valid @RequestBody MoveRequest request){return service.moveItem(jwt.getSubject(),setId,versionId,request);}
    @DeleteMapping("/{setId}/versions/{versionId}/items/{itemId}") public VersionView remove(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID setId,@PathVariable UUID versionId,@PathVariable UUID itemId,@RequestParam long expectedVersion){return service.removeItem(jwt.getSubject(),setId,versionId,itemId,expectedVersion);}
    @PostMapping("/{setId}/versions/{versionId}/publish") public VersionView publishVersion(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID setId,@PathVariable UUID versionId,@Valid @RequestBody PublishRequest request){return service.publish(jwt.getSubject(),setId,versionId,request.expectedVersion());}
    @PostMapping("/{setId}/versions/{versionId}/next-draft") public VersionView nextDraft(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID setId,@PathVariable UUID versionId){return service.nextDraft(jwt.getSubject(),setId,versionId);}
    @PostMapping("/{setId}/versions/{versionId}/variant-draft") public VersionView variant(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID setId,@PathVariable UUID versionId,@Valid @RequestBody CreateVariantRequest request){return service.variantDraft(jwt.getSubject(),setId,versionId,request);}
    @PostMapping("/{setId}/versions/{versionId}/retire") public VersionView retire(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID setId,@PathVariable UUID versionId){return service.retire(jwt.getSubject(),setId,versionId);}
}
