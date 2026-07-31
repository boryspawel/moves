package com.motionecosystem.anatomyreference.api;

import java.util.List;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Typed visual metadata; exercise-set results intentionally use their persisted snapshots instead. */
@RestController
@RequestMapping("/api/v1/anatomy")
@SecurityRequirement(name = "oidc")
class AnatomyReferenceController {
    private final AnatomyReferenceQueryPort anatomy;

    AnatomyReferenceController(AnatomyReferenceQueryPort anatomy) { this.anatomy = anatomy; }

    @GetMapping("/visual-regions")
    List<AnatomyReferenceQueryPort.VisualRegionSnapshot> activeVisualRegions() {
        return anatomy.activeVisualRegions();
    }
}
