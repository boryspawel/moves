package com.motionecosystem.exerciseimport;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** JPA read model used solely by the import pipeline. */
@Repository
@RequiredArgsConstructor
class ImportCatalogReadRepository {
    private final EntityManager entityManager;

    boolean activeDictionaryContains(String type, String code) {
        String entity = dictionaryEntity(type);
        return !entityManager.createQuery("select item.code from " + entity + " item where item.code = :code and item.active = true", String.class)
                .setParameter("code", code).setMaxResults(1).getResultList().isEmpty();
    }

    List<DictionaryOption> activeDictionaryOptions(String type) {
        return entityManager.createQuery("select item.code, item.displayName from " + dictionaryEntity(type)
                        + " item where item.active = true order by item.code", Object[].class)
                .setMaxResults(100).getResultList().stream()
                .map(row -> new DictionaryOption((String) row[0], (String) row[1])).toList();
    }

    private static String dictionaryEntity(String type) {
        return switch (type) {
            case "EQUIPMENT" -> "ExerciseImportEquipmentDictionaryJpaEntity";
            case "POSITION" -> "ExerciseImportPositionDictionaryJpaEntity";
            case "DOSE_UNIT" -> "ExerciseImportDoseUnitDictionaryJpaEntity";
            default -> throw new IllegalArgumentException("unsupported dictionary");
        };
    }

    record DictionaryOption(String value, String displayName) {}

    UUID publishedAnatomyId(String code) {
        List<UUID> anatomyIds = entityManager.createQuery("select item.id from AnatomicalStructureJpaEntity item where item.code = :code and item.status = com.motionecosystem.anatomyreference.domain.PublicationStatus.PUBLISHED", UUID.class)
                .setParameter("code", code).setMaxResults(1).getResultList();
        return anatomyIds.isEmpty() ? null : anatomyIds.getFirst();
    }

    UUID exerciseIdForVersion(UUID versionId) {
        return entityManager.createQuery("select version.exerciseId from ExerciseVersion version where version.id = :id", UUID.class)
                .setParameter("id", versionId).getSingleResult();
    }

    String canonicalName(UUID exerciseId) {
        List<String> canonicalNames = entityManager.createQuery(
                        "select exercise.canonicalName from Exercise exercise where exercise.id = :id", String.class)
                .setParameter("id", exerciseId)
                .setMaxResults(1)
                .getResultList();
        return canonicalNames.isEmpty() ? null : canonicalNames.getFirst();
    }

    List<UUID> exactNameOrAlias(String locale, String name) {
        return entityManager.createQuery("""
                select exercise.id from Exercise exercise
                where lower(exercise.canonicalName) = :name or exists (
                    select alias.id from ImportedExerciseAliasJpaEntity alias
                    where alias.exerciseId = exercise.id and alias.locale = :locale and alias.normalizedAlias = :name)
                order by exercise.id
                """, UUID.class).setParameter("name", name).setParameter("locale", locale).getResultList();
    }

    boolean aliasMatch(UUID exerciseId, String locale, String name) {
        return !entityManager.createQuery("select alias.id from ImportedExerciseAliasJpaEntity alias where alias.exerciseId=:exerciseId and alias.locale=:locale and alias.normalizedAlias=:value", UUID.class)
                .setParameter("exerciseId", exerciseId).setParameter("locale", locale).setParameter("value", name).setMaxResults(1).getResultList().isEmpty();
    }
    boolean patternMatch(UUID exerciseId, String value) { return exists("select version.id from ImportCatalogVersionMatchJpaEntity version where version.exerciseId=:exerciseId and :value member of version.movementPatterns", exerciseId, null, value); }
    boolean positionMatch(UUID exerciseId, String value) { return exists("select item.id from ImportedExerciseMovementCharacteristicJpaEntity item where item.versionId in (select version.id from ExerciseVersion version where version.exerciseId=:exerciseId) and item.positionCode=:value", exerciseId, null, value); }
    boolean unilateralMatch(UUID exerciseId, boolean value) { return exists("select item.id from ImportedExerciseMovementCharacteristicJpaEntity item where item.versionId in (select version.id from ExerciseVersion version where version.exerciseId=:exerciseId) and item.unilateral=:flag", exerciseId, value, null); }
    boolean loadNatureMatch(UUID exerciseId, String value) { return exists("select item.id from ImportedExerciseMovementCharacteristicJpaEntity item where item.versionId in (select version.id from ExerciseVersion version where version.exerciseId=:exerciseId) and item.loadNature=:value", exerciseId, null, value); }
    boolean equipmentMatch(UUID exerciseId, String equipment) {
        if (equipment.isEmpty()) return !exists("select item.id from ImportedExerciseEquipmentJpaEntity item where item.id.versionId in (select version.id from ExerciseVersion version where version.exerciseId=:exerciseId)", exerciseId, null, null);
        return exists("select item.id from ImportedExerciseEquipmentJpaEntity item where item.id.versionId in (select version.id from ExerciseVersion version where version.exerciseId=:exerciseId) and item.id.equipmentCode=:value", exerciseId, null, equipment);
    }

    private boolean exists(String jpql, UUID exerciseId, Boolean flag, String value) {
        var query = entityManager.createQuery(jpql, UUID.class).setParameter("exerciseId", exerciseId).setMaxResults(1);
        if (flag != null) query.setParameter("flag", flag);
        if (value != null) query.setParameter("value", value);
        return !query.getResultList().isEmpty();
    }
}
