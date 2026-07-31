package com.motionecosystem.exercisecatalog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Read-only PostgreSQL search projection.  It uses a bounded number of native JPA queries so the
 * public picker never materializes catalog aggregates or performs a query per result.
 */
@Service
@Transactional(Transactional.TxType.SUPPORTS)
public class ExerciseCatalogSearchService {
    private static final String DEFAULT_LOCALE = "pl-PL";
    private static final int MAX_QUERY_LENGTH = 120;
    private static final Set<String> SUPPORTED_LOCALES = Set.of(DEFAULT_LOCALE);
    private static final Set<String> ANATOMY_TYPES = Set.of("BODY_REGION", "MUSCLE_GROUP", "MUSCLE", "TENDON_GROUP", "JOINT");
    private static final Set<String> PURPOSES = Set.of("TRAINING", "THERAPEUTIC_EXERCISE", "ASSESSMENT", "WARM_UP", "RECOVERY");
    private final ObjectMapper objectMapper;
    private final CatalogService catalog;
    @PersistenceContext private EntityManager entityManager;

    public ExerciseCatalogSearchService(ObjectMapper objectMapper, CatalogService catalog) {
        this.objectMapper = objectMapper;
        this.catalog = catalog;
    }

    public SearchPage search(SearchRequest raw) {
        SearchRequest request = normalized(raw);
        Cursor cursor = decodeCursor(request.cursor(), fingerprint(request));
        Sql base = base(request, null);
        String score = scoreExpression(request.query());
        StringBuilder sql = new StringBuilder("WITH candidates AS (SELECT e.id exercise_id, v.id version_id, v.version_number, "
                + "COALESCE(t.name,e.canonical_name) title, t.summary, v.stimulus_type, v.technical_level, v.media_reference, "
                + "v.published_at, exercise_catalog.fold_search_text(COALESCE(t.name,e.canonical_name)) normalized_name, " + score + " score "
                + "FROM exercise_catalog.exercise e JOIN exercise_catalog.exercise_version v ON v.exercise_id=e.id "
                + "LEFT JOIN exercise_catalog.exercise_version_text t ON t.exercise_version_id=v.id AND t.locale=:locale "
                + "WHERE " + base.where + ") SELECT * FROM candidates");
        if (cursor != null) sql.append(" WHERE ").append(seek(request.sort(), cursor));
        sql.append(" ORDER BY ").append(order(request.sort())).append(" LIMIT :fetchLimit");
        Query query = entityManager.createNativeQuery(sql.toString());
        bind(query, base.params);
        query.setParameter("locale", request.locale());
        if (request.query() != null) query.setParameter("query", request.query());
        query.setParameter("fetchLimit", request.limit() + 1);
        if (cursor != null) {
            if (request.sort() == Sort.RELEVANCE) query.setParameter("cursorScore", cursor.score());
            query.setParameter("cursorName", cursor.name());
            query.setParameter("cursorId", cursor.id());
            if (request.sort() == Sort.RECENTLY_PUBLISHED) query.setParameter("cursorPublishedAt", cursor.publishedAt());
        }
        @SuppressWarnings("unchecked") List<Object[]> rows = query.getResultList();
        boolean hasMore = rows.size() > request.limit();
        if (hasMore) rows = rows.subList(0, request.limit());
        List<Result> results = enrich(rows);
        String next = hasMore && !rows.isEmpty() ? encodeCursor(cursorOf(rows.getLast()), fingerprint(request)) : null;
        return new SearchPage(results, next, hasMore, facets(request));
    }

    /** Minimal explicit hand-off projection for the future set picker; it does not persist anything. */
    public Selection selectable(UUID exerciseVersionId) {
        Preview preview = preview(exerciseVersionId);
        return new Selection(preview.exerciseId(), preview.exerciseVersionId(), preview.title(), preview.versionNumber(),
                preview.technicalLevel(), preview.movementPatterns(), preview.requiredEquipment());
    }

    public Preview preview(UUID versionId) {
        Object value = entityManager.createNativeQuery("""
                SELECT count(*) FROM exercise_catalog.exercise_version v
                WHERE v.id=:id AND v.status='PUBLISHED' AND NOT EXISTS (
                  SELECT 1 FROM exercise_catalog.exercise_version newer WHERE newer.exercise_id=v.exercise_id
                  AND newer.status='PUBLISHED' AND newer.version_number > v.version_number)
                """).setParameter("id", versionId).getSingleResult();
        if (((Number) value).longValue() != 1) throw problem(HttpStatus.NOT_FOUND, "EXERCISE_VERSION_NOT_SELECTABLE", "Exercise version is not selectable");
        CatalogService.ExerciseCatalogDetailView detail = catalog.publishedDetail(versionId);
        String mediaReference = text(entityManager.createNativeQuery(
                "SELECT media_reference FROM exercise_catalog.exercise_version WHERE id=:id")
                .setParameter("id", versionId).getSingleResult());
        return new Preview(detail.exerciseId(), detail.versionId(), detail.versionNumber(), detail.canonicalName(), detail.instruction(),
                detail.technicalLevel(), detail.movementPatterns(), detail.requiredEquipment(), detail.anatomyContributions(),
                mediaReference, detail.environment(), detail.stimulusType());
    }

    private List<Result> enrich(List<Object[]> rows) {
        if (rows.isEmpty()) return List.of();
        List<UUID> ids = rows.stream().map(row -> uuid(row[1])).toList();
        Map<UUID, List<String>> patterns = strings("exercise_catalog.exercise_version_movement_pattern", "movement_pattern", "exercise_version_id", ids);
        Map<UUID, List<String>> equipment = strings("exercise_catalog.exercise_equipment", "equipment_code", "exercise_version_id", ids);
        Map<UUID, List<Anatomy>> anatomy = anatomy(ids);
        return rows.stream().map(row -> new Result(uuid(row[0]), uuid(row[1]), ((Number) row[2]).intValue(), text(row[3]), text(row[4]),
                text(row[5]), text(row[6]), patterns.getOrDefault(uuid(row[1]), List.of()), equipment.getOrDefault(uuid(row[1]), List.of()),
                anatomy.getOrDefault(uuid(row[1]), List.of()), text(row[7]), true)).toList();
    }

    private Map<UUID, List<String>> strings(String table, String value, String id, List<UUID> ids) {
        Sql in = new Sql(); String clause = in.addList("id", ids);
        Query query = entityManager.createNativeQuery("SELECT " + id + ", " + value + " FROM " + table + " WHERE " + id + " IN (" + clause + ") ORDER BY " + id + ", " + value);
        bind(query, in.params);
        Map<UUID, List<String>> output = new LinkedHashMap<>();
        for (Object[] row : rows(query)) output.computeIfAbsent(uuid(row[0]), unused -> new ArrayList<>()).add(text(row[1]));
        return output;
    }

    private Map<UUID, List<Anatomy>> anatomy(List<UUID> ids) {
        Sql in = new Sql(); String clause = in.addList("id", ids);
        Query query = entityManager.createNativeQuery("""
                SELECT c.exercise_version_id, a.id, a.code, a.display_name, a.type, c.contribution_role
                FROM exercise_catalog.exercise_contribution c JOIN anatomy_reference.anatomical_structure a ON a.id=c.anatomical_structure_id
                WHERE c.exercise_version_id IN (""" + clause + ") AND a.status='PUBLISHED' ORDER BY c.exercise_version_id, a.display_name, a.id");
        bind(query, in.params);
        Map<UUID, List<Anatomy>> output = new LinkedHashMap<>();
        for (Object[] row : rows(query)) output.computeIfAbsent(uuid(row[0]), unused -> new ArrayList<>())
                .add(new Anatomy(uuid(row[1]), text(row[2]), text(row[3]), text(row[4]), text(row[5])));
        return output;
    }

    private List<Facet> facets(SearchRequest request) {
        List<Facet> result = new ArrayList<>();
        result.addAll(facet(request, "movementPatterns", "mp.movement_pattern", "exercise_catalog.exercise_version_movement_pattern mp", "mp.exercise_version_id=v.id"));
        result.addAll(facet(request, "technicalLevels", "v.technical_level", null, null));
        result.addAll(facet(request, "equipment", "eq.equipment_code", "exercise_catalog.exercise_equipment eq", "eq.exercise_version_id=v.id"));
        result.addAll(facet(request, "positionCodes", "mc.position_code", "exercise_catalog.exercise_movement_characteristic mc", "mc.exercise_version_id=v.id"));
        result.addAll(facet(request, "unilateral", "mc.unilateral::text", "exercise_catalog.exercise_movement_characteristic mc", "mc.exercise_version_id=v.id"));
        result.addAll(facet(request, "purposes", "p.purpose", "exercise_catalog.exercise_version_purpose p", "p.exercise_version_id=v.id"));
        result.addAll(facet(request, "anatomyStructureTypes", "a.type", "exercise_catalog.exercise_contribution c JOIN anatomy_reference.anatomical_structure a ON a.id=c.anatomical_structure_id", "c.exercise_version_id=v.id AND a.status='PUBLISHED'"));
        result.addAll(facet(request, "anatomyStructureIds", "a.id::text", "exercise_catalog.exercise_contribution c JOIN anatomy_reference.anatomical_structure a ON a.id=c.anatomical_structure_id", "c.exercise_version_id=v.id AND a.status='PUBLISHED'"));
        return List.copyOf(result);
    }

    private List<Facet> facet(SearchRequest request, String group, String value, String join, String joinOn) {
        Sql base = base(request, group);
        String from = "exercise_catalog.exercise e JOIN exercise_catalog.exercise_version v ON v.exercise_id=e.id LEFT JOIN exercise_catalog.exercise_version_text t ON t.exercise_version_id=v.id AND t.locale=:locale";
        if (join != null) from += " JOIN " + join + " ON " + joinOn;
        Query query = entityManager.createNativeQuery("SELECT " + value + ", count(DISTINCT v.id) FROM " + from + " WHERE " + base.where
                + " GROUP BY " + value + " ORDER BY " + value);
        bind(query, base.params); query.setParameter("locale", request.locale()); if (request.query() != null) query.setParameter("query", request.query());
        Set<String> active = active(request, group);
        return rows(query).stream().map(row -> new Facet(group, text(row[0]), null, ((Number) row[1]).longValue(), active.contains(text(row[0])))).toList();
    }

    private Sql base(SearchRequest request, String exclude) {
        Sql sql = new Sql();
        StringBuilder where = new StringBuilder("v.status='PUBLISHED' AND NOT EXISTS (SELECT 1 FROM exercise_catalog.exercise_version newer WHERE newer.exercise_id=v.exercise_id AND newer.status='PUBLISHED' AND newer.version_number>v.version_number)");
        if (request.query() != null) where.append(" AND (").append(textMatch()).append(")");
        filter(where, sql, exclude, "movementPatterns", request.movementPatterns(), "EXISTS (SELECT 1 FROM exercise_catalog.exercise_version_movement_pattern f WHERE f.exercise_version_id=v.id AND f.movement_pattern IN (%s))");
        filter(where, sql, exclude, "technicalLevels", request.technicalLevels(), "v.technical_level IN (%s)");
        filter(where, sql, exclude, "equipment", request.equipment(), "(EXISTS (SELECT 1 FROM exercise_catalog.exercise_equipment f WHERE f.exercise_version_id=v.id AND f.equipment_code IN (%s)) OR EXISTS (SELECT 1 FROM exercise_catalog.exercise_version_equipment f WHERE f.exercise_version_id=v.id AND f.equipment IN (%s)))", true);
        filter(where, sql, exclude, "positionCodes", request.positionCodes(), "EXISTS (SELECT 1 FROM exercise_catalog.exercise_movement_characteristic f WHERE f.exercise_version_id=v.id AND f.position_code IN (%s))");
        if (!Objects.equals(exclude, "unilateral") && request.unilateral() != null) where.append(" AND EXISTS (SELECT 1 FROM exercise_catalog.exercise_movement_characteristic f WHERE f.exercise_version_id=v.id AND f.unilateral=:unilateral)");
        filter(where, sql, exclude, "purposes", request.purposes(), "EXISTS (SELECT 1 FROM exercise_catalog.exercise_version_purpose f WHERE f.exercise_version_id=v.id AND f.purpose IN (%s))");
        filter(where, sql, exclude, "anatomyStructureIds", request.anatomyStructureIds(), "EXISTS (SELECT 1 FROM exercise_catalog.exercise_contribution c WHERE c.exercise_version_id=v.id AND c.anatomical_structure_id IN (%s))");
        filter(where, sql, exclude, "anatomyStructureTypes", request.anatomyStructureTypes(), "EXISTS (SELECT 1 FROM exercise_catalog.exercise_contribution c JOIN anatomy_reference.anatomical_structure a ON a.id=c.anatomical_structure_id WHERE c.exercise_version_id=v.id AND a.status='PUBLISHED' AND a.type IN (%s))");
        if (request.unilateral() != null && !Objects.equals(exclude, "unilateral")) sql.params.put("unilateral", request.unilateral());
        return new Sql(where.toString(), sql.params);
    }

    private static String textMatch() {
        return "exercise_catalog.fold_search_text(COALESCE(t.name,e.canonical_name)) LIKE '%' || :query || '%' OR "
                + "exercise_catalog.fold_search_text(v.instruction) LIKE '%' || :query || '%' OR "
                + "EXISTS (SELECT 1 FROM exercise_catalog.exercise_alias a WHERE a.exercise_id=e.id AND a.locale=:locale AND exercise_catalog.fold_search_text(a.alias) LIKE '%' || :query || '%') OR "
                + "similarity(exercise_catalog.fold_search_text(COALESCE(t.name,e.canonical_name)), :query) >= 0.35";
    }

    private static String scoreExpression(String query) {
        if (query == null) return "0";
        return "CASE WHEN exercise_catalog.fold_search_text(COALESCE(t.name,e.canonical_name))=:query THEN 0 "
                + "WHEN exercise_catalog.fold_search_text(COALESCE(t.name,e.canonical_name)) LIKE :query || '%' THEN 1 "
                + "WHEN EXISTS (SELECT 1 FROM exercise_catalog.exercise_alias a WHERE a.exercise_id=e.id AND a.locale=:locale AND exercise_catalog.fold_search_text(a.alias)=:query) THEN 2 "
                + "WHEN exercise_catalog.fold_search_text(COALESCE(t.name,e.canonical_name)) LIKE '%' || :query || '%' THEN 3 "
                + "WHEN EXISTS (SELECT 1 FROM exercise_catalog.exercise_alias a WHERE a.exercise_id=e.id AND a.locale=:locale AND exercise_catalog.fold_search_text(a.alias) LIKE '%' || :query || '%') THEN 4 "
                + "WHEN exercise_catalog.fold_search_text(v.instruction) LIKE '%' || :query || '%' THEN 5 ELSE 6 END";
    }

    private static String order(Sort sort) {
        return switch (sort) { case RELEVANCE -> "score ASC, normalized_name ASC, version_id ASC"; case NAME -> "normalized_name ASC, version_id ASC"; case RECENTLY_PUBLISHED -> "published_at DESC, normalized_name ASC, version_id ASC"; };
    }
    private static String seek(Sort sort, Cursor c) {
        return switch (sort) {
            case RELEVANCE -> "(score > :cursorScore OR (score=:cursorScore AND (normalized_name > :cursorName OR (normalized_name=:cursorName AND version_id > cast(:cursorId as uuid)))))";
            case NAME -> "(normalized_name > :cursorName OR (normalized_name=:cursorName AND version_id > cast(:cursorId as uuid)))";
            case RECENTLY_PUBLISHED -> "(published_at < cast(:cursorPublishedAt as timestamptz) OR (published_at=cast(:cursorPublishedAt as timestamptz) AND (normalized_name > :cursorName OR (normalized_name=:cursorName AND version_id > cast(:cursorId as uuid)))))";
        };
    }

    private SearchRequest normalized(SearchRequest input) {
        if (input == null) throw problem(HttpStatus.BAD_REQUEST, "INVALID_SEARCH_REQUEST", "Search request is required");
        String locale = input.locale() == null || input.locale().isBlank() ? DEFAULT_LOCALE : input.locale().trim();
        if (!SUPPORTED_LOCALES.contains(locale)) throw problem(HttpStatus.BAD_REQUEST, "UNSUPPORTED_LOCALE", "Unsupported locale: " + locale);
        String query = fold(input.query());
        if (query != null && query.length() > MAX_QUERY_LENGTH) throw problem(HttpStatus.BAD_REQUEST, "QUERY_TOO_LONG", "Query must not exceed " + MAX_QUERY_LENGTH + " characters");
        int limit = input.limit() == null ? 20 : input.limit();
        if (limit < 1 || limit > 50) throw problem(HttpStatus.BAD_REQUEST, "INVALID_LIMIT", "limit must be between 1 and 50");
        List<String> movementPatterns = codes(input.movementPatterns());
        List<String> technicalLevels = codes(input.technicalLevels());
        List<String> anatomyTypes = codes(input.anatomyStructureTypes());
        validateKnown("movementPatterns", movementPatterns, java.util.Arrays.stream(MovementPattern.values()).map(Enum::name).collect(java.util.stream.Collectors.toSet()));
        validateKnown("technicalLevels", technicalLevels, java.util.Arrays.stream(TechnicalLevel.values()).map(Enum::name).collect(java.util.stream.Collectors.toSet()));
        validateKnown("anatomyStructureTypes", anatomyTypes, ANATOMY_TYPES);
        List<String> purposes = codes(input.purposes());
        validateKnown("purposes", purposes, PURPOSES);
        return new SearchRequest(query, locale, movementPatterns, technicalLevels, codes(input.equipment()), codes(input.positionCodes()),
                input.unilateral(), uuids(input.anatomyStructureIds()), anatomyTypes, purposes,
                input.sort() == null ? Sort.RELEVANCE : input.sort(), limit, input.cursor());
    }
    private static List<String> codes(Collection<String> input) { return input == null ? List.of() : input.stream().filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty()).map(value -> value.toUpperCase(Locale.ROOT)).distinct().sorted().toList(); }
    private static List<UUID> uuids(Collection<UUID> input) { return input == null ? List.of() : input.stream().filter(Objects::nonNull).distinct().sorted(Comparator.comparing(UUID::toString)).toList(); }
    private static void validateKnown(String field, Collection<String> values, Set<String> known) { if (!known.containsAll(values)) throw problem(HttpStatus.BAD_REQUEST, "UNKNOWN_FILTER", "Unknown value in " + field); }
    public static String fold(String value) { if (value == null || value.isBlank()) return null; return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT).replace('ą','a').replace('ć','c').replace('ę','e').replace('ł','l').replace('ń','n').replace('ó','o').replace('ś','s').replace('ź','z').replace('ż','z'); }

    private Set<String> active(SearchRequest request, String group) { return switch (group) { case "movementPatterns" -> Set.copyOf(request.movementPatterns()); case "technicalLevels" -> Set.copyOf(request.technicalLevels()); case "equipment" -> Set.copyOf(request.equipment()); case "positionCodes" -> Set.copyOf(request.positionCodes()); case "unilateral" -> request.unilateral() == null ? Set.of() : Set.of(request.unilateral().toString()); case "purposes" -> Set.copyOf(request.purposes()); case "anatomyStructureTypes" -> Set.copyOf(request.anatomyStructureTypes()); case "anatomyStructureIds" -> request.anatomyStructureIds().stream().map(UUID::toString).collect(java.util.stream.Collectors.toSet()); default -> Set.of(); }; }
    private void filter(StringBuilder where, Sql sql, String exclude, String name, Collection<?> values, String template) { filter(where, sql, exclude, name, values, template, false); }
    private void filter(StringBuilder where, Sql sql, String exclude, String name, Collection<?> values, String template, boolean duplicate) { if (Objects.equals(exclude, name) || values == null || values.isEmpty()) return; String clause=sql.addList(name, values); if (duplicate) { String second=sql.addList(name + "Legacy", values); where.append(" AND ").append(template.formatted(clause, second)); } else where.append(" AND ").append(template.formatted(clause)); }
    private Cursor cursorOf(Object[] row) { return new Cursor(((Number) row[10]).intValue(), text(row[9]), uuid(row[1]).toString(), row[8] == null ? null : row[8].toString(), null); }
    private String fingerprint(SearchRequest r) { return sha256(r.query()+"|"+r.locale()+"|"+r.movementPatterns()+"|"+r.technicalLevels()+"|"+r.equipment()+"|"+r.positionCodes()+"|"+r.unilateral()+"|"+r.anatomyStructureIds()+"|"+r.anatomyStructureTypes()+"|"+r.purposes()+"|"+r.sort()); }
    private String encodeCursor(Cursor cursor, String fingerprint) { try { return Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(new Cursor(cursor.score(), cursor.name(), cursor.id(), cursor.publishedAt(), fingerprint))); } catch (JacksonException e) { throw new IllegalStateException(e); } }
    private Cursor decodeCursor(String raw, String fingerprint) { if (raw == null || raw.isBlank()) return null; try { Cursor c=objectMapper.readValue(Base64.getUrlDecoder().decode(raw), Cursor.class); if (!Objects.equals(c.fingerprint(), fingerprint) || c.id()==null || c.name()==null) throw new IllegalArgumentException(); return c; } catch (Exception e) { throw problem(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "Cursor is malformed or belongs to another search"); } }
    private static String sha256(String value) { try { byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder out=new StringBuilder(); for(byte b:bytes) out.append(String.format("%02x", b)); return out.toString(); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    private static ExerciseCatalogSearchProblem problem(HttpStatus status, String code, String message) { return new ExerciseCatalogSearchProblem(status, code, message); }
    @SuppressWarnings("unchecked") private static List<Object[]> rows(Query q) { return q.getResultList(); }
    private static void bind(Query query, Map<String,Object> params) { params.forEach(query::setParameter); }
    private static UUID uuid(Object value) { return value instanceof UUID id ? id : UUID.fromString(value.toString()); }
    private static String text(Object value) { return value == null ? null : value.toString(); }

    private static final class Sql { final String where; final Map<String,Object> params; int number; Sql(){this("",new LinkedHashMap<>());} Sql(String where, Map<String,Object> params){this.where=where;this.params=params;} String addList(String prefix, Collection<?> values){ List<String> names=new ArrayList<>(); for(Object value:values){String key=prefix+number++;names.add(":"+key);params.put(key,value);} return String.join(",",names);}}
    private record Cursor(int score, String name, String id, String publishedAt, String fingerprint) { }

    public enum Sort { RELEVANCE, NAME, RECENTLY_PUBLISHED }
    public record SearchRequest(@Size(max=120) String query, String locale, List<String> movementPatterns, List<String> technicalLevels,
                                List<String> equipment, List<String> positionCodes, Boolean unilateral, List<UUID> anatomyStructureIds,
                                List<String> anatomyStructureTypes, List<String> purposes, Sort sort, @Min(1) @Max(50) Integer limit, String cursor) { }
    public record SearchPage(List<Result> results, String nextCursor, boolean hasMore, List<Facet> facets) { public SearchPage { results=List.copyOf(results); facets=List.copyOf(facets); } }
    public record Result(UUID exerciseId, UUID exerciseVersionId, int versionNumber, String title, String summary, String exerciseType,
                         String technicalLevel, List<String> movementPatterns, List<String> equipment, List<Anatomy> keyAnatomy,
                         String mediaReference, boolean selectable) { public Result { movementPatterns=List.copyOf(movementPatterns); equipment=List.copyOf(equipment); keyAnatomy=List.copyOf(keyAnatomy); } }
    public record Facet(String group, String value, String labelKey, long count, boolean active) { }
    public record Anatomy(UUID structureId, String code, String displayName, String type, String role) { }
    public record Preview(UUID exerciseId, UUID exerciseVersionId, int versionNumber, String title, String instruction, String technicalLevel,
                          List<String> movementPatterns, List<String> requiredEquipment, List<CatalogService.PublicAnatomyContributionView> anatomy,
                          String mediaReference, String environment, String exerciseType) { public Preview { movementPatterns=List.copyOf(movementPatterns); requiredEquipment=List.copyOf(requiredEquipment); anatomy=List.copyOf(anatomy); } }
    public record Selection(UUID exerciseId, UUID exerciseVersionId, String title, int versionNumber, String technicalLevel,
                            List<String> movementPatterns, List<String> requiredEquipment) { public Selection { movementPatterns=List.copyOf(movementPatterns); requiredEquipment=List.copyOf(requiredEquipment); } }
}
