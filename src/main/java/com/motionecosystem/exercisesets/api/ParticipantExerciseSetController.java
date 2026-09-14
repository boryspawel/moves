package com.motionecosystem.exercisesets.api;

import java.util.List;
import java.util.UUID;
import com.motionecosystem.exercisesets.api.ExerciseSetDtos.ParticipantLibraryEntry;
import com.motionecosystem.exercisesets.api.ExerciseSetDtos.ParticipantVersionView;
import com.motionecosystem.exercisesets.ports.ExerciseSetCommandPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/participant/exercise-sets")
@PreAuthorize("hasRole('PARTICIPANT')")
@RequiredArgsConstructor
public class ParticipantExerciseSetController {
    private final ExerciseSetCommandPort service;
    @GetMapping public List<ParticipantLibraryEntry> list(@AuthenticationPrincipal Jwt jwt) { return service.participantLibrary(jwt.getSubject()); }
    @GetMapping("/{versionId}") public ParticipantVersionView version(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID versionId) { return service.participantVersion(jwt.getSubject(), versionId); }
}
