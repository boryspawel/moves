package com.motionecosystem.loadanalysis.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motionecosystem.loadanalysis.LoadAnalysisPersistence;
import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort.LoadProfile;
import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort.CompletenessIssue;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaLoadAnalysisAdapter implements LoadAnalysisPersistence {
    private final EntityManager entityManager;
    private final ObjectMapper json;

    @Override
    public Optional<LoadProfile> find(UUID revisionId, String checksum, String algorithm,
                                      String configuration) {
        List<LoadSnapshotEntity> found = entityManager.createQuery("""
                SELECT item FROM LoadSnapshotEntity item
                WHERE item.revisionId=:revision AND item.checksum=:checksum
                  AND item.algorithm=:algorithm AND item.configuration=:configuration
                """, LoadSnapshotEntity.class).setParameter("revision", revisionId)
                .setParameter("checksum", checksum).setParameter("algorithm", algorithm)
                .setParameter("configuration", configuration).setMaxResults(1).getResultList();
        return found.stream().findFirst().map(this::view);
    }

    @Override
    public LoadProfile save(LoadProfile profile) {
        CalculationVersionId versionId = new CalculationVersionId(
                profile.algorithmVersion(), profile.configurationVersion());
        if (entityManager.find(CalculationVersionEntity.class, versionId) == null)
            entityManager.persist(new CalculationVersionEntity(profile.algorithmVersion(),
                    profile.configurationVersion(), profile.calculatedAt()));
        LoadSnapshotEntity snapshot = new LoadSnapshotEntity(profile);
        snapshot.completenessIssues = json(profile.completenessIssues());
        entityManager.persist(snapshot);
        profile.observations().forEach(item -> entityManager.persist(
                new LoadObservationEntity(profile.snapshotId(), item)));
        profile.aggregates().forEach(item -> entityManager.persist(
                new LoadAggregateEntity(profile.snapshotId(), item)));
        entityManager.flush();
        return profile;
    }

    private LoadProfile view(LoadSnapshotEntity snapshot) {
        var observations = entityManager.createQuery("""
                SELECT item FROM LoadObservationEntity item WHERE item.snapshotId=:id
                ORDER BY item.sessionId, item.prescriptionId, item.contributionId
                """, LoadObservationEntity.class).setParameter("id", snapshot.id).getResultList()
                .stream().map(LoadObservationEntity::view).toList();
        var aggregates = entityManager.createQuery("""
                SELECT item FROM LoadAggregateEntity item WHERE item.snapshotId=:id
                ORDER BY item.scope, item.scopeKey, item.structureId, item.channel, item.family
                """, LoadAggregateEntity.class).setParameter("id", snapshot.id).getResultList()
                .stream().map(LoadAggregateEntity::view).toList();
        return new LoadProfile(snapshot.id,snapshot.revisionId,snapshot.checksum,snapshot.algorithm,
                snapshot.configuration,snapshot.catalogVersion,snapshot.calculatedAt,observations,aggregates,
                issues(snapshot.completenessIssues));
    }

    private String json(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception error) { throw new IllegalStateException("cannot persist load completeness issues", error); }
    }

    private List<CompletenessIssue> issues(String value) {
        try { return json.readValue(value, json.getTypeFactory().constructCollectionType(List.class, CompletenessIssue.class)); }
        catch (Exception error) { throw new IllegalStateException("corrupt load completeness issues", error); }
    }
}
