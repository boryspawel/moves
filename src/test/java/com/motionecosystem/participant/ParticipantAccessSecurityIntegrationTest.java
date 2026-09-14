package com.motionecosystem.participant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.motionecosystem.application.MotionEcosystemApplication;
import com.motionecosystem.support.PostgresTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import jakarta.servlet.http.Cookie;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = MotionEcosystemApplication.class, properties = "participant-access.trusted-frontend-url=http://localhost:4200")
@Import(PostgresTestConfiguration.class)
class ParticipantAccessSecurityIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy securityFilterChain;
    private MockMvc mvc;
    @BeforeEach void setUp() { mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain).build(); }
    @Test void claimRequiresAuthenticationWhileOnlyContextIsPublic() throws Exception {
        mvc.perform(post("/api/v1/participant-access/claim").header("Origin", "http://localhost:4200")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void claimRejectsAnAuthenticatedButUnverifiedJwtBeforeUsingClaimContext() throws Exception {
        mvc.perform(post("/api/v1/participant-access/claim").header("Origin", "http://localhost:4200")
                        .cookie(new Cookie("participant_claim_context", "context-secret"))
                        .with(jwt().jwt(token -> token.subject("unverified-participant")
                                .claim("email", "participant@example.test").claim("email_verified", false))
                                .authorities(new SimpleGrantedAuthority("ROLE_PARTICIPANT")))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }
}
