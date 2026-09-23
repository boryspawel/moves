package com.motionecosystem.support;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/** Keeps migration-provided anatomy reference data intact between shared-database tests. */
public final class AnatomyReferenceFixtureTracker {

    private final JdbcTemplate jdbc;
    private Set<UUID> structureIds = Set.of();
    private Set<UUID> relationIds = Set.of();

    public AnatomyReferenceFixtureTracker(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void snapshot() {
        structureIds = ids("anatomy_reference.anatomical_structure");
        relationIds = ids("anatomy_reference.anatomical_structure_relation");
    }

    public void removeAddedFixtures() {
        deleteAdded("anatomy_reference.anatomical_structure_relation", relationIds);
        deleteAdded("anatomy_reference.anatomical_structure", structureIds);
    }

    private Set<UUID> ids(String table) {
        return new HashSet<>(jdbc.queryForList("SELECT id FROM " + table, UUID.class));
    }

    private void deleteAdded(String table, Set<UUID> originalIds) {
        List<UUID> addedIds = ids(table).stream().filter(id -> !originalIds.contains(id)).toList();
        if (addedIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(addedIds.size(), "?"));
        jdbc.update("DELETE FROM " + table + " WHERE id IN (" + placeholders + ")", addedIds.toArray());
    }
}
