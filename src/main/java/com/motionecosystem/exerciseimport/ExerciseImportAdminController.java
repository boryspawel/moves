package com.motionecosystem.exerciseimport;

import com.motionecosystem.exerciseimport.ExerciseImportService.BatchView;
import com.motionecosystem.exerciseimport.ExerciseImportService.CreateSource;
import com.motionecosystem.exerciseimport.ExerciseImportService.MappingDecision;
import com.motionecosystem.exerciseimport.ExerciseImportService.MatchDecision;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/exercise-import")
@SecurityRequirement(name = "oidc")
@PreAuthorize("hasAnyRole('CONTENT_ADMIN','SYSTEM_ADMIN')")
class ExerciseImportAdminController {
    private final ExerciseImportService imports;
    ExerciseImportAdminController(ExerciseImportService imports) { this.imports = imports; }

    @PostMapping("/sources")
    ResponseEntity<ExerciseImportService.SourceView> createSource(
            @AuthenticationPrincipal Jwt jwt, @RequestBody CreateSource request) {
        var created = imports.createSource(jwt.getSubject(), request);
        return ResponseEntity.created(URI.create("/api/v1/admin/exercise-import/sources/" + created.id())).body(created);
    }

    @GetMapping("/sources")
    List<ExerciseImportService.SourceView> sources() { return imports.sources(); }

    @PostMapping(path = "/batches", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN','SYSTEM_ADMIN') and (!#forceReprocess or hasRole('SYSTEM_ADMIN'))")
    ResponseEntity<UploadAccepted> upload(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String requestKey,
            @RequestParam UUID sourceId,
            @RequestParam(defaultValue = "false") boolean forceReprocess,
            @RequestPart("file") MultipartFile file) {
        BatchView batch = imports.upload(jwt.getSubject(), sourceId, requestKey, forceReprocess, file);
        String status = "/api/v1/admin/exercise-import/batches/" + batch.id();
        return ResponseEntity.accepted().location(URI.create(status)).body(new UploadAccepted(batch.id(), status));
    }

    @GetMapping("/batches/{id}")
    BatchView batch(@PathVariable UUID id) { return imports.batch(id); }

    @PostMapping("/batches/{id}/restart")
    ResponseEntity<Void> restart(@PathVariable UUID id) {
        imports.restart(id); return ResponseEntity.accepted().build();
    }

    @GetMapping("/batches/{id}/records")
    ExerciseImportService.RecordPage records(@PathVariable UUID id,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return imports.records(id,status,severity,page,size);
    }

    @GetMapping("/records/{id}")
    ExerciseImportService.RecordDetail record(@PathVariable UUID id) { return imports.record(id); }

    @PostMapping("/records/{id}/match")
    ExerciseImportService.RecordDetail match(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,@RequestBody MatchDecision request) {
        return imports.decideMatch(jwt.getSubject(),id,request);
    }

    @PostMapping("/mappings/{id}/decision")
    ExerciseImportService.MappingView mapping(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,@RequestBody MappingDecision request) {
        return imports.decideMapping(jwt.getSubject(),id,request);
    }

    @PostMapping("/records/{id}/create-draft")
    ResponseEntity<Map<String,UUID>> draft(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) {
        UUID versionId=imports.createDraft(jwt.getSubject(),id);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("exerciseVersionId",versionId));
    }

    @GetMapping("/batches/{id}/issues")
    ResponseEntity<String> issues(@PathVariable UUID id,
            @RequestParam(defaultValue="jsonl") String format,
            @RequestParam(required=false) String severity) {
        boolean csv="csv".equalsIgnoreCase(format);
        String filename="exercise-import-"+id+"-issues."+(csv?"csv":"jsonl");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+filename+"\"")
                .contentType(MediaType.parseMediaType(csv?"text/csv":"application/x-ndjson"))
                .body(imports.exportIssues(id,severity,csv));
    }

    record UploadAccepted(UUID batchId,String statusUrl) { }
}
