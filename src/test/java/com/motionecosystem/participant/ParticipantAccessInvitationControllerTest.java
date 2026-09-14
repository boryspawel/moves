package com.motionecosystem.participant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ParticipantAccessInvitationControllerTest {
    private final ParticipantAccessInvitationService service = Mockito.mock(ParticipantAccessInvitationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var controller = new ParticipantAccessInvitationController(service,
                Clock.fixed(Instant.parse("2026-09-14T10:00:00Z"), ZoneOffset.UTC));
        ReflectionTestUtils.setField(controller, "trustedFrontendUrl", "http://localhost:4200");
        ReflectionTestUtils.setField(controller, "cookieSecure", false);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void bootstrapRejectsMissingOrWrongOriginBeforeReadingToken() throws Exception {
        mvc.perform(post("/api/v1/participant-access/context").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"raw-token\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/participant-access/context").header("Origin", "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"raw-token\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void bootstrapSetsBoundedHttpOnlyCookieAndDoesNotReturnRawToken() throws Exception {
        when(service.bootstrap("raw-token")).thenReturn(new ParticipantAccessInvitationService.ContextView(
                "context-secret", Instant.parse("2026-09-14T10:30:00Z"), "PENDING"));
        mvc.perform(post("/api/v1/participant-access/context").header("Origin", "http://localhost:4200")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"raw-token\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("participant_claim_context=context-secret"),
                        org.hamcrest.Matchers.containsString("HttpOnly"),
                        org.hamcrest.Matchers.containsString("SameSite=Lax"),
                        org.hamcrest.Matchers.containsString("Path=/api/v1/participant-access"))))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("raw-token"))));
    }
}
