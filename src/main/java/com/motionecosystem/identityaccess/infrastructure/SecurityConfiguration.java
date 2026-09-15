package com.motionecosystem.identityaccess.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http,
                                    JwtAuthenticationConverter authenticationConverter,
                                    CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/api/v1/participant-access/context")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(authenticationConverter)))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${participant-access.trusted-frontend-url:https://localhost:4200}") String trustedFrontendUrl) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(normalizedOrigin(trustedFrontendUrl)));
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/participant-access/**", config);
        return source;
    }

    private static String normalizedOrigin(String configured) {
        java.net.URI uri = java.net.URI.create(configured);
        if (uri.getScheme() == null || uri.getHost() == null || uri.getUserInfo() != null
                || (uri.getPath() != null && !uri.getPath().isBlank() && !"/".equals(uri.getPath()))
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("participant-access.trusted-frontend-url must be an origin");
        }
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
    }

    @Bean
    OAuth2TokenValidator<Jwt> jwtValidator(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${security.oauth2.audience}") String audience) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new AudienceValidator(audience),
                new SubjectValidator());
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            OAuth2TokenValidator<Jwt> jwtValidator) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(jwtValidator);
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter(
            @Value("${security.oauth2.client-id}") String clientId) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakAuthoritiesConverter(clientId));
        return converter;
    }
}
