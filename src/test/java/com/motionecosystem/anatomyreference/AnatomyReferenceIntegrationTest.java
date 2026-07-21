package com.motionecosystem.anatomyreference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.motionecosystem.anatomyreference.api.AnatomyReferenceQueryPort;
import com.motionecosystem.anatomyreference.api.AnatomyReferenceQueryPort.StructureStatus;
import com.motionecosystem.application.MotionEcosystemApplication;
import com.motionecosystem.support.PostgresTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = MotionEcosystemApplication.class)
@Import(PostgresTestConfiguration.class)
class AnatomyReferenceIntegrationTest {

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy securityFilterChain;
    @Autowired JdbcTemplate jdbc;
    @Autowired AnatomyReferenceQueryPort queries;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
    }

    @AfterEach
    void clean() {
        jdbc.execute("""
                TRUNCATE TABLE
                    audit.audit_event,
                    anatomy_reference.anatomical_structure_relation,
                    anatomy_reference.anatomical_structure
                CASCADE
                """);
    }

    @Test
    void contentAdminCreatesPublishesAndWithdrawsStructureWhileNonAdminIsRejected() throws Exception {
        mvc.perform(post("/api/v1/admin/anatomical-structures")
                        .with(participant())
                        .contentType("application/json")
                        .content(structureRequest("KNEE_JOINT", "JOINT", "Knee joint")))
                .andExpect(status().isForbidden());

        UUID kneeId = create("KNEE_JOINT", "JOINT", "Knee joint");
        UUID regionId = create("LOWER_LIMB", "BODY_REGION", "Lower limb");
        assertThat(queries.findStructure(kneeId)).get()
                .extracting(AnatomyReferenceQueryPort.AnatomicalStructureSnapshot::status)
                .isEqualTo(StructureStatus.DRAFT);

        mvc.perform(post("/api/v1/admin/anatomical-structures/{id}/publish", kneeId)
                        .with(contentAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mvc.perform(post("/api/v1/admin/anatomical-structures/relations")
                        .with(contentAdmin())
                        .contentType("application/json")
                        .content(relationRequest(regionId, kneeId, "PART_OF")))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/admin/anatomical-structures/{id}/withdraw", kneeId)
                        .with(contentAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit.audit_event", Long.class)).isEqualTo(4);
    }

    @Test
    void codeIsUniqueAndPublicPortDoesNotExposeJpaEntity() throws Exception {
        UUID kneeId = create("KNEE_JOINT", "JOINT", "Knee joint");

        mvc.perform(post("/api/v1/admin/anatomical-structures")
                        .with(contentAdmin())
                        .contentType("application/json")
                        .content(structureRequest("knee_joint", "JOINT", "Another knee")))
                .andExpect(status().isConflict());

        AnatomyReferenceQueryPort.AnatomicalStructureSnapshot snapshot = queries.findStructure(kneeId).orElseThrow();
        assertThat(snapshot.getClass().isAnnotationPresent(jakarta.persistence.Entity.class)).isFalse();
        assertThat(snapshot.code()).isEqualTo("KNEE_JOINT");
    }

    @Test
    void rejectsSelfLinkDuplicateAndIndirectCycleAndReturnsDeterministicAncestorPath() throws Exception {
        UUID root = create("ROOT_REGION", "BODY_REGION", "Root region");
        UUID middle = create("MIDDLE_GROUP", "MUSCLE_GROUP", "Middle group");
        UUID leaf = create("LEAF_MUSCLE", "MUSCLE", "Leaf muscle");
        addRelation(root, middle, "PART_OF");
        addRelation(middle, leaf, "MEMBER_OF");

        mvc.perform(post("/api/v1/admin/anatomical-structures/relations")
                        .with(contentAdmin()).contentType("application/json")
                        .content(relationRequest(leaf, leaf, "PART_OF")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/anatomical-structures/relations")
                        .with(contentAdmin()).contentType("application/json")
                        .content(relationRequest(root, middle, "PART_OF")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/admin/anatomical-structures/relations")
                        .with(contentAdmin()).contentType("application/json")
                        .content(relationRequest(leaf, root, "PART_OF")))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/v1/admin/anatomical-structures/{id}/ancestors", leaf)
                        .with(contentAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].steps[0].structure.code").value("MIDDLE_GROUP"))
                .andExpect(jsonPath("$[0].steps[0].relationType").value("MEMBER_OF"))
                .andExpect(jsonPath("$[0].steps[1].structure.code").value("ROOT_REGION"));

        assertThat(queries.ancestorPaths(leaf)).singleElement().satisfies(path ->
                assertThat(path.steps()).extracting(step -> step.structure().code())
                        .containsExactly("MIDDLE_GROUP", "ROOT_REGION"));
    }

    @Test
    void serializesOppositeConcurrentRelationsSoOnlyAcyclicWriteSucceeds() throws Exception {
        UUID first = create("FIRST_NODE", "BODY_REGION", "First node");
        UUID second = create("SECOND_NODE", "BODY_REGION", "Second node");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> forward = executor.submit(() -> concurrentRelation(
                    first, second, ready, start));
            Future<Integer> reverse = executor.submit(() -> concurrentRelation(
                    second, first, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(forward.get(20, TimeUnit.SECONDS), reverse.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM anatomy_reference.anatomical_structure_relation", Long.class)).isEqualTo(1);
    }

    @Test
    void openApiPublishesAnatomyAdministrationContract() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/anatomical-structures']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/anatomical-structures/{structureId}/ancestors']")
                        .exists());
    }

    private UUID create(String code, String type, String name) throws Exception {
        mvc.perform(post("/api/v1/admin/anatomical-structures")
                        .with(contentAdmin())
                        .contentType("application/json")
                        .content(structureRequest(code, type, name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
        return jdbc.queryForObject(
                "SELECT id FROM anatomy_reference.anatomical_structure WHERE code = ?", UUID.class, code);
    }

    private void addRelation(UUID parent, UUID child, String type) throws Exception {
        mvc.perform(post("/api/v1/admin/anatomical-structures/relations")
                        .with(contentAdmin()).contentType("application/json")
                        .content(relationRequest(parent, child, type)))
                .andExpect(status().isOk());
    }

    private int concurrentRelation(UUID parent, UUID child, CountDownLatch ready,
                                   CountDownLatch start) throws Exception {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return mvc.perform(post("/api/v1/admin/anatomical-structures/relations")
                        .with(contentAdmin()).contentType("application/json")
                        .content(relationRequest(parent, child, "PART_OF")))
                .andReturn().getResponse().getStatus();
    }

    private static String structureRequest(String code, String type, String name) {
        return """
                {
                  "code":"%s",
                  "type":"%s",
                  "displayName":"%s",
                  "sidePolicy":"LEFT_RIGHT",
                  "externalOntology":"UBERON",
                  "externalOntologyId":"UBERON:test",
                  "taxonomyVersion":1
                }
                """.formatted(code, type, name);
    }

    private static String relationRequest(UUID parent, UUID child, String type) {
        return """
                {"parentId":"%s","childId":"%s","relationType":"%s"}
                """.formatted(parent, child, type);
    }

    private static JwtRequestPostProcessor contentAdmin() {
        return jwt().jwt(builder -> builder.subject("anatomy-admin").audience(List.of("motion-api")))
                .authorities(new SimpleGrantedAuthority("ROLE_CONTENT_ADMIN"));
    }

    private static JwtRequestPostProcessor participant() {
        return jwt().jwt(builder -> builder.subject("participant").audience(List.of("motion-api")))
                .authorities(new SimpleGrantedAuthority("ROLE_PARTICIPANT"));
    }
}
