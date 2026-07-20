package com.motionecosystem.identityaccess.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity")
class CurrentIdentityController {

    @GetMapping("/me")
    @Operation(summary = "Return the authenticated external identity")
    @SecurityRequirement(name = "oidc")
    IdentityResponse current(@AuthenticationPrincipal Jwt jwt) {
        List<String> audiences = jwt.getAudience() == null ? List.of() : jwt.getAudience();
        return new IdentityResponse(jwt.getSubject(), audiences);
    }

    record IdentityResponse(String subject, List<String> audiences) {
    }
}
