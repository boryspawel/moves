package com.motionecosystem.anatomyreference.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AnatomicalStructureTest {

    @Test
    void publishedStructureIsSemanticallyImmutableAndCanOnlyBeWithdrawn() {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        AnatomicalStructure structure = AnatomicalStructure.draft(UUID.randomUUID(), "KNEE_JOINT",
                AnatomicalStructureType.JOINT, "Knee joint", SidePolicy.LEFT_RIGHT,
                "UBERON", "UBERON:0001465", 1, "content-admin", createdAt);

        structure.publish(createdAt.plusSeconds(60));

        assertThat(structure.status()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThatThrownBy(structure::requireRelationMutable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("semantically immutable");
        assertThatThrownBy(() -> structure.publish(createdAt.plusSeconds(120)))
                .isInstanceOf(IllegalStateException.class);

        structure.withdraw(createdAt.plusSeconds(180));
        assertThat(structure.status()).isEqualTo(PublicationStatus.WITHDRAWN);
    }

    @Test
    void externalReferenceMustBeCompleteAndRelationCannotPointToItself() {
        assertThatThrownBy(() -> AnatomicalStructure.draft(UUID.randomUUID(), "KNEE_JOINT",
                AnatomicalStructureType.JOINT, "Knee joint", SidePolicy.LEFT_RIGHT,
                "UBERON", null, 1, "content-admin", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provided together");

        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new AnatomicalStructureRelation(UUID.randomUUID(), id, id,
                RelationType.PART_OF, Instant.now(), "content-admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("itself");
    }
}
