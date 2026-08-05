package com.motionecosystem.participantgoals;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ParticipantGoalControllerTest {
    private final ParticipantGoalService goals = Mockito.mock(ParticipantGoalService.class);
    private final UUID participantId = UUID.randomUUID();
    private final UUID goalId = UUID.randomUUID();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ParticipantGoalController(goals))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
    }

    @Test
    void rejectsMissingRequiredCreateInputs() throws Exception {
        mvc.perform(post(goalsPath()).param("actingContext", "TRAINER").header("Idempotency-Key", "key")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(goals);
    }

    @Test
    void rejectsGeneralFitnessAtTheApiBoundary() throws Exception {
        mvc.perform(post(goalsPath()).param("actingContext", "TRAINER").header("Idempotency-Key", "key")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"perspective\":\"GENERAL_FITNESS\",\"title\":\"Finish 5k\",\"priority\":60}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(goals);
    }

    @Test
    void rejectsMissingExpectedVersionForEveryMutation() throws Exception {
        mvc.perform(put(goalPath()).param("actingContext", "TRAINER").header("Idempotency-Key", "key")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Finish 5k\",\"priority\":60}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(goalPath() + "/achieve").param("actingContext", "TRAINER").header("Idempotency-Key", "key")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(goalPath() + "/cancel").param("actingContext", "TRAINER").header("Idempotency-Key", "key")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(goals);
    }

    @Test
    void validatesObservationCommandAndLeavesUnitAndMeasurementMethodToTheOutcomeSnapshot() throws Exception {
        String observationPath = goalPath() + "/observations";
        mvc.perform(post(observationPath).param("actingContext", "TRAINER").header("Idempotency-Key", "key")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        var command = new ParticipantGoalController.ParticipantGoalObservationRequest(UUID.randomUUID(),
                java.math.BigDecimal.valueOf(4.2), java.time.Instant.parse("2030-06-10T12:00:00Z"), null, null).toCommand();
        org.assertj.core.api.Assertions.assertThat(command.measurementMethod()).isNull();

        verifyNoInteractions(goals);
    }

    private String goalsPath() { return "/api/v1/specialist/clients/" + participantId + "/goals"; }
    private String goalPath() { return goalsPath() + "/" + goalId; }
}
