package com.motionecosystem.application;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(
        title = "Motion Ecosystem API",
        version = "v1",
        description = "API for participant and specialist workflows"
))
@SecurityScheme(
        name = "oidc",
        type = SecuritySchemeType.OPENIDCONNECT,
        openIdConnectUrl = "${spring.security.oauth2.resourceserver.jwt.issuer-uri}/.well-known/openid-configuration"
)
class OpenApiConfiguration {
}
