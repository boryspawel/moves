package com.motionecosystem.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.file.Files;
import java.nio.file.Path;

import com.motionecosystem.support.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = MotionEcosystemApplication.class)
@Import(PostgresTestConfiguration.class)
class OpenApiSnapshotIntegrationTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    FilterChainProxy securityFilterChain;

    @Test
    void exposesAndOptionallySnapshotsTheRealContract() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
        String contract = mvc.perform(get("/v3/api-docs"))
                .andReturn().getResponse().getContentAsString();
        assertThat(contract).contains("/api/v1/onboarding", "/api/v1/planned-sessions", "/api/v1/gamification/me");

        String output = System.getProperty("openapi.snapshot");
        if (output != null && !output.isBlank()) {
            Path path = Path.of(output).toAbsolutePath().normalize();
            Files.createDirectories(path.getParent());
            Files.writeString(path, contract);
        }
    }
}
