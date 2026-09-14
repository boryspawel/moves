package com.motionecosystem.exercisesets.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import com.motionecosystem.application.MotionEcosystemApplication;
import com.motionecosystem.exercisesets.domain.ExerciseSetModel.VariantKind;
import com.motionecosystem.exercisesets.domain.ExerciseSetModel.VersionStatus;
import com.motionecosystem.identityaccess.domain.PrincipalAccount;
import com.motionecosystem.identityaccess.domain.PrincipalAccountRepository;
import com.motionecosystem.support.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = MotionEcosystemApplication.class)
@Import(PostgresTestConfiguration.class)
class ExerciseSetVersionGrantJpaIntegrationTest {
    @Autowired ExerciseSetRepository sets;
    @Autowired ExerciseSetVersionRepository versions;
    @Autowired ExerciseSetVersionGrantRepository grants;
    @Autowired PrincipalAccountRepository accounts;

    @Test @Transactional
    void persistsOneExactVersionGrantAndExcludesItsRevokedRowFromParticipantLibraryQuery() {
        Instant now = Instant.parse("2026-09-14T12:00:00Z");
        UUID owner = accounts.save(PrincipalAccount.create("grant-owner-" + UUID.randomUUID(), now)).id();
        UUID participant = UUID.randomUUID();
        var set = new ExerciseSetEntities.ExerciseSetEntity(); set.id = UUID.randomUUID(); set.ownerAccountId = owner; set.createdAt = now; set.updatedAt = now; sets.save(set);
        var version = new ExerciseSetEntities.ExerciseSetVersionEntity(); version.id = UUID.randomUUID(); version.exerciseSetId = set.id; version.versionNumber = 1; version.status = VersionStatus.PUBLISHED; version.authorAccountId = owner; version.variantKind = VariantKind.BASE; version.createdAt = now; version.updatedAt = now; version.publishedAt = now; versions.save(version);
        var grant = new ExerciseSetEntities.ExerciseSetVersionGrantEntity(); grant.id = UUID.randomUUID(); grant.versionId = version.id; grant.participantId = participant; grant.grantedByAccountId = owner; grant.grantedAt = now; grants.saveAndFlush(grant);

        assertThat(versions.findPublishedAvailableToParticipantId(participant)).extracting(value -> value.id).containsExactly(version.id);

        grant.revokedByAccountId = owner; grant.revokedAt = now.plusSeconds(1); grants.saveAndFlush(grant);
        assertThat(versions.findPublishedAvailableToParticipantId(participant)).isEmpty();
    }
}
