package com.motionecosystem.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.motionecosystem.support.PostgresTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class HttpFoundationIntegrationTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    FilterChainProxy securityFilterChain;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
    }

    @Test
    void exposesHealthAndOpenApiWithoutAuthentication() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        String openApi = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(openApi).contains("Motion Ecosystem API", "/api/v1/identity/me");
    }

    @Test
    void protectsDomainApiAndReturnsAuthenticatedSubject() throws Exception {
        mvc.perform(get("/api/v1/identity/me"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/identity/me").with(jwt().jwt(token -> token
                        .subject("keycloak-subject")
                        .audience(java.util.List.of("motion-api")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("keycloak-subject"));
    }
}
