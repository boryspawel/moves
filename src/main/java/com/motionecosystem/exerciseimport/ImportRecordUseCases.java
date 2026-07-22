package com.motionecosystem.exerciseimport;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.exerciseimport.api.CreateExerciseDraft;
import com.motionecosystem.exerciseimport.api.FindExerciseMatch;
import com.motionecosystem.exerciseimport.api.NormalizeImportRecord;
import com.motionecosystem.exerciseimport.api.ValidateImportRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportRecordUseCases implements NormalizeImportRecord, ValidateImportRecord,
        FindExerciseMatch, CreateExerciseDraft {

    static final String NORMALIZATION_VERSION = "unicode-nfkc-sort-v1";
    static final String MATCH_VERSION = "deterministic-catalog-v1";
    private static final Set<String> PURPOSES = Set.of(
            "TRAINING", "THERAPEUTIC_EXERCISE", "ASSESSMENT", "WARM_UP", "RECOVERY");
    private static final Set<String> MOVEMENT_PATTERNS = Set.of(
            "SQUAT", "HINGE", "PUSH", "PULL", "LUNGE", "CARRY", "ROTATION",
            "LOCOMOTION", "BREATHING", "MOBILITY", "OTHER");
    private static final Set<String> STIMULUS = Set.of(
            "STRENGTH", "ENDURANCE", "POWER", "MOBILITY", "BALANCE", "MOTOR_CONTROL", "RECOVERY");
    private static final Set<String> FATIGUE = Set.of("LOW", "MODERATE", "HIGH");
    private static final Set<String> TECHNICAL = Set.of("FOUNDATIONAL", "INTERMEDIATE", "ADVANCED");
    private static final Set<String> ENVIRONMENTS = Set.of("HOME", "GYM", "OUTDOOR", "CLINIC", "ANY");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final AuditRecorder audit;

    @Override
    @Transactional
    public void normalize(UUID recordId) {
        RecordData record = record(recordId, false);
        if (!record.status.equals("PARSED")) return;
        try {
            JsonNode raw = json.readTree(record.rawPayload);
            requireContract(raw);
            ObjectNode normalized = json.createObjectNode();
            normalized.put("sourceRecordKey", requiredText(raw, "sourceRecordKey", 240));
            normalized.put("locale", normalizeLocale(raw.path("locale").asText(record.defaultLocale)));
            normalized.put("name", clean(requiredText(raw, "name", 160)));
            normalized.set("aliases", normalizedTextArray(raw.path("aliases"), true));
            normalized.set("instructions", normalizedTextArrayRequired(raw.path("instructions")));
            normalized.set("purposes", normalizedEnumArray(raw.path("purposes"), PURPOSES, "/purposes"));
            normalized.set("movementPatterns", normalizedEnumArray(
                    raw.path("movementPatterns"), MOVEMENT_PATTERNS, "/movementPatterns"));
            normalized.put("stimulusType", enumValue(raw, "stimulusType", STIMULUS));
            normalized.put("fatigueProfile", enumValue(raw, "fatigueProfile", FATIGUE));
            normalized.put("technicalLevel", enumValue(raw, "technicalLevel", TECHNICAL));
            normalized.put("environment", enumValue(raw, "environment", ENVIRONMENTS));
            normalized.set("equipment", dictionaryArray(record, raw.path("equipment"), "EQUIPMENT"));
            normalized.put("position", dictionaryValue(record, raw.path("position").asText("STANDING"), "POSITION"));
            normalized.put("unilateral", raw.path("unilateral").asBoolean(false));
            normalized.put("loadNature", upper(raw.path("loadNature").asText("BODYWEIGHT")));
            normalized.set("doseCapabilities", normalizeDose(record, raw.path("doseCapabilities")));
            normalized.set("loadCharacteristics", normalizeLoad(raw.path("loadCharacteristics")));
            normalized.set("contributions", normalizeContributions(record, raw.path("contributions")));

            String canonical = json.writeValueAsString(normalized);
            String hash = sha256(canonical);
            boolean blocked = unresolvedMappings(recordId);
            jdbc.update("""
                    UPDATE exercise_import.import_record
                       SET source_record_key=?, normalized_payload=CAST(? AS jsonb), normalized_sha256=?,
                           normalization_version=?, status=?, updated_at=?, version=version+1
                     WHERE id=?
                    """, normalized.path("sourceRecordKey").asText(), canonical, hash,
                    NORMALIZATION_VERSION, blocked ? "BLOCKED_BY_MAPPING" : "NORMALIZED", sql(clock.instant()), recordId);
            for (String unsafe : List.of("contraindications", "injuries", "treatment", "safeFor")) {
                if (raw.has(unsafe)) issue(record, "UNVERIFIED_SAFETY_FIELD", "NORMALIZE", "WARNING",
                        "/" + unsafe, "Pole bezpieczeństwa zachowano wyłącznie w raw_payload; nie tworzy reguły safety.");
            }
        } catch (RecordProblem problem) {
            invalidate(record, "NORMALIZE", problem);
        } catch (Exception malformed) {
            invalidate(record, "NORMALIZATION_FAILED", "NORMALIZE", "/", "Nie można znormalizować rekordu.");
        }
    }

    @Override
    @Transactional
    public void validate(UUID recordId) {
        RecordData record = record(recordId, false);
        if (!record.status.equals("NORMALIZED")) return;
        try {
            JsonNode normalized = json.readTree(record.normalizedPayload);
            JsonNode raw = json.readTree(record.rawPayload);
            if (!record.licenseVerified || !raw.path("license").path("redistributionAllowed").asBoolean(false)) {
                issue(record, "LICENSE_NOT_VERIFIED", "VALIDATE", "BLOCKER", "/license",
                        "Źródło i rekord muszą mieć zweryfikowane prawo do użycia.");
                setStatus(recordId, "BLOCKED_BY_LICENSE");
                return;
            }
            requireNonEmpty(normalized.path("instructions"), "/instructions", "INSTRUCTIONS_REQUIRED");
            requireNonEmpty(normalized.path("purposes"), "/purposes", "PURPOSE_REQUIRED");
            requireNonEmpty(normalized.path("movementPatterns"), "/movementPatterns", "MOVEMENT_REQUIRED");
            requireNonEmpty(normalized.path("doseCapabilities"), "/doseCapabilities", "DOSE_REQUIRED");
            requireNonEmpty(normalized.path("loadCharacteristics"), "/loadCharacteristics", "LOAD_PROFILE_REQUIRED");
            requireNonEmpty(normalized.path("contributions"), "/contributions", "ANATOMY_REQUIRED");
            setStatus(recordId, "NORMALIZED");
        } catch (RecordProblem problem) {
            issue(record, problem.code, "VALIDATE", "ERROR", problem.pointer, problem.getMessage());
            setStatus(recordId, "INVALID");
        } catch (Exception malformed) {
            invalidate(record, "VALIDATION_FAILED", "VALIDATE", "/", "Nie można zwalidować rekordu.");
        }
    }

    @Override
    @Transactional
    public void findMatches(UUID recordId) {
        RecordData record = record(recordId, false);
        if (!record.status.equals("NORMALIZED")) return;
        SourceReference reference = jdbc.query("""
                SELECT ref.exercise_id, ref.normalized_sha256
                  FROM exercise_import.import_source_reference ref
                 WHERE ref.source_id=? AND ref.source_record_key=?
                """, rs -> rs.next() ? new SourceReference(rs.getObject(1, UUID.class), rs.getString(2)) : null,
                record.sourceId, record.sourceRecordKey);
        if (reference != null) {
            if (reference.hash.equals(record.normalizedSha256)) {
                jdbc.update("UPDATE exercise_import.import_record SET status='UNCHANGED', matched_exercise_id=?, updated_at=?, version=version+1 WHERE id=?",
                        reference.exerciseId, sql(clock.instant()), recordId);
                jdbc.update("""
                        UPDATE exercise_import.import_source_reference
                           SET last_record_id=?,updated_at=?,version=version+1
                         WHERE source_id=? AND source_record_key=?
                        """, recordId, sql(clock.instant()), record.sourceId, record.sourceRecordKey);
            } else {
                jdbc.update("UPDATE exercise_import.import_record SET status='READY_FOR_DRAFT', matched_exercise_id=?, updated_at=?, version=version+1 WHERE id=?",
                        reference.exerciseId, sql(clock.instant()), recordId);
            }
            return;
        }

        jdbc.update("DELETE FROM exercise_import.import_match_candidate WHERE record_id=?", recordId);
        List<Candidate> candidates = candidates(record);
        int rank = 1;
        for (Candidate candidate : candidates.stream().sorted(Comparator
                .comparingDouble(Candidate::score).reversed().thenComparing(Candidate::exerciseId)).limit(5).toList()) {
            jdbc.update("""
                    INSERT INTO exercise_import.import_match_candidate
                        (id,record_id,exercise_id,rank,score,reasons,algorithm_version,version)
                    VALUES (?,?,?,?,?,CAST(? AS jsonb),?,0)
                    """, UUID.randomUUID(), recordId, candidate.exerciseId, rank++, candidate.score,
                    candidate.reasons, MATCH_VERSION);
        }
        setStatus(recordId, candidates.isEmpty() ? "READY_FOR_DRAFT" : "MATCH_CANDIDATES");
    }

    @Override
    @Transactional
    public UUID createDraft(UUID recordId, String actorSubject) {
        RecordData record = record(recordId, true);
        if (record.draftVersionId != null) return record.draftVersionId;
        if (!record.status.equals("READY_FOR_DRAFT")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "record is not ready for draft");
        }
        try {
            JsonNode data = json.readTree(record.normalizedPayload);
            Instant now = clock.instant();
            UUID exerciseId = record.matchedExerciseId;
            if (exerciseId == null) {
                exerciseId = UUID.randomUUID();
                jdbc.update("INSERT INTO exercise_catalog.exercise(id,canonical_name,created_at,created_by_subject) VALUES (?,?,?,?)",
                        exerciseId, data.path("name").asText(), sql(now), actorSubject);
            }
            jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", rs -> {
                rs.next(); return null;
            }, exerciseId.toString());
            Integer maximum = jdbc.queryForObject(
                    "SELECT COALESCE(MAX(version_number),0) FROM exercise_catalog.exercise_version WHERE exercise_id=?",
                    Integer.class, exerciseId);
            int number = maximum == null ? 1 : maximum + 1;
            UUID versionId = UUID.randomUUID();
            String primaryPattern = sortedStrings(data.path("movementPatterns")).getFirst();
            String instruction = String.join("\n", sortedByInput(data.path("instructions")));
            jdbc.update("""
                    INSERT INTO exercise_catalog.exercise_version(
                        id,exercise_id,version_number,status,instruction,movement_pattern,stimulus_type,
                        fatigue_profile,technical_level,environment,created_at,profile_schema_version,
                        locale,semantic_sha256,import_record_id,version)
                    VALUES (?,?,?,'DRAFT',?,?,?,?,?,?,?,2,?,?,?,0)
                    """, versionId, exerciseId, number, instruction, primaryPattern,
                    data.path("stimulusType").asText(), data.path("fatigueProfile").asText(),
                    data.path("technicalLevel").asText(), data.path("environment").asText(), sql(now),
                    data.path("locale").asText(), record.normalizedSha256, recordId);
            insertSemanticChildren(versionId, exerciseId, record.sourceId, data, actorSubject, now);
            jdbc.update("UPDATE exercise_import.import_record SET status='DRAFTED', matched_exercise_id=?, draft_version_id=?, updated_at=?, version=version+1 WHERE id=?",
                    exerciseId, versionId, sql(now), recordId);
            jdbc.update("""
                    INSERT INTO exercise_import.import_source_reference(
                        id,source_id,source_record_key,exercise_id,latest_exercise_version_id,
                        normalized_sha256,first_record_id,last_record_id,updated_at,version)
                    VALUES (?,?,?,?,?,?,?,?,?,0)
                    ON CONFLICT(source_id,source_record_key) DO UPDATE SET
                        exercise_id=excluded.exercise_id, latest_exercise_version_id=excluded.latest_exercise_version_id,
                        normalized_sha256=excluded.normalized_sha256, last_record_id=excluded.last_record_id,
                        updated_at=excluded.updated_at, version=exercise_import.import_source_reference.version+1
                    """, UUID.randomUUID(), record.sourceId, record.sourceRecordKey, exerciseId, versionId,
                    record.normalizedSha256, recordId, recordId, sql(now));
            audit.record(actorSubject, number == 1 ? "IMPORTED_EXERCISE_DRAFT_CREATED" : "IMPORTED_EXERCISE_VERSION_DRAFT_CREATED",
                    "ExerciseVersion", versionId);
            return versionId;
        } catch (DuplicateKeyException concurrent) {
            RecordData existing = record(recordId, false);
            if (existing.draftVersionId != null) return existing.draftVersionId;
            throw new ResponseStatusException(HttpStatus.CONFLICT, "concurrent draft creation", concurrent);
        } catch (ResponseStatusException status) {
            throw status;
        } catch (Exception invalid) {
            log.warn("Creating draft for import record {} failed", recordId, invalid);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "normalized record cannot create a draft", invalid);
        }
    }

    private void insertSemanticChildren(UUID versionId, UUID exerciseId, UUID sourceId, JsonNode data,
                                        String actor, Instant now) {
        for (String purpose : sortedStrings(data.path("purposes"))) {
            jdbc.update("INSERT INTO exercise_catalog.exercise_version_purpose(exercise_version_id,purpose,provenance_source_id) VALUES (?,?,?)",
                    versionId, purpose, sourceId);
        }
        jdbc.update("INSERT INTO exercise_catalog.exercise_version_text(id,exercise_version_id,locale,name,provenance_source_id) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), versionId, data.path("locale").asText(), data.path("name").asText(), sourceId);
        int step = 1;
        for (String instruction : sortedByInput(data.path("instructions"))) {
            jdbc.update("INSERT INTO exercise_catalog.exercise_instruction_step(id,exercise_version_id,locale,step_number,instruction,provenance_source_id) VALUES (?,?,?,?,?,?)",
                    UUID.randomUUID(), versionId, data.path("locale").asText(), step++, instruction, sourceId);
        }
        LinkedHashSet<String> aliases = new LinkedHashSet<>(sortedStrings(data.path("aliases")));
        aliases.add(data.path("name").asText());
        for (String alias : aliases) {
            jdbc.update("""
                    INSERT INTO exercise_catalog.exercise_alias(id,exercise_id,locale,alias,normalized_alias,provenance_source_id)
                    VALUES (?,?,?,?,?,?) ON CONFLICT(exercise_id,locale,normalized_alias) DO NOTHING
                    """, UUID.randomUUID(), exerciseId, data.path("locale").asText(), alias,
                    normalizedAlias(alias), sourceId);
        }
        for (String pattern : sortedStrings(data.path("movementPatterns"))) {
            jdbc.update("INSERT INTO exercise_catalog.exercise_version_movement_pattern(exercise_version_id,movement_pattern) VALUES (?,?)",
                    versionId, pattern);
            jdbc.update("""
                    INSERT INTO exercise_catalog.exercise_movement_characteristic(
                        id,exercise_version_id,movement_pattern,position_code,unilateral,load_nature,provenance_source_id)
                    VALUES (?,?,?,?,?,?,?)
                    """, UUID.randomUUID(), versionId, pattern, data.path("position").asText(),
                    data.path("unilateral").asBoolean(), data.path("loadNature").asText(), sourceId);
        }
        for (String equipment : sortedStrings(data.path("equipment"))) {
            jdbc.update("INSERT INTO exercise_catalog.exercise_version_equipment(exercise_version_id,equipment) VALUES (?,?)",
                    versionId, equipment);
            jdbc.update("INSERT INTO exercise_catalog.exercise_equipment(exercise_version_id,equipment_code,required,provenance_source_id) VALUES (?,?,TRUE,?)",
                    versionId, equipment, sourceId);
        }
        for (JsonNode dose : data.path("doseCapabilities")) {
            jdbc.update("INSERT INTO exercise_catalog.exercise_dose_capability(exercise_version_id,unit_code,minimum_value,maximum_value,provenance_source_id) VALUES (?,?,?,?,?)",
                    versionId, dose.path("unit").asText(), decimalText(dose.path("minimum")),
                    decimalText(dose.path("maximum")), sourceId);
        }
        for (JsonNode load : data.path("loadCharacteristics")) {
            jdbc.update("""
                    INSERT INTO exercise_catalog.exercise_load_characteristic(
                        id,exercise_version_id,movement_plane,contraction_type,range_of_motion,
                        characteristic_type,created_at,created_by_subject) VALUES (?,?,?,?,?,?,?,?)
                    """, UUID.randomUUID(), versionId, load.path("movementPlane").asText(),
                    load.path("contractionType").asText(), load.path("rangeOfMotion").asText(),
                    load.path("characteristicType").asText(), sql(now), actor);
        }
        UUID evidenceId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO exercise_catalog.evidence_source(
                    id,exercise_version_id,citation,evidence_grade,created_at,created_by_subject,
                    source_type,license_code,provenance_source_id) VALUES (?,?,?,'SOURCE_ASSERTION',?,?, 'IMPORT',
                    (SELECT license_code FROM exercise_import.import_source WHERE id=?),?)
                """, evidenceId, versionId, "Import source record " + data.path("sourceRecordKey").asText(),
                sql(now), actor, sourceId, sourceId);
        for (JsonNode contribution : data.path("contributions")) {
            UUID structure = jdbc.query("SELECT id FROM anatomy_reference.anatomical_structure WHERE code=? AND status='PUBLISHED'",
                    rs -> rs.next() ? rs.getObject(1, UUID.class) : null, contribution.path("anatomyCode").asText());
            if (structure == null) throw new RecordProblem("UNKNOWN_ANATOMY", "/contributions", "Nieznana opublikowana anatomia.");
            UUID contributionId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO exercise_catalog.exercise_contribution(
                        id,exercise_version_id,anatomical_structure_id,contribution_role,load_channel,
                        contribution_band,coefficient_low,coefficient_high,confidence_class,evidence_grade,
                        calculation_role,side_rule,created_at,created_by_subject)
                    VALUES (?,?,?,?,?,?,?,?,?,'SOURCE_ASSERTION','ALLOCATION',?,?,?)
                    """, contributionId, versionId, structure, contribution.path("role").asText(),
                    contribution.path("loadChannel").asText(), contribution.path("band").asText(),
                    contribution.path("coefficientLow").decimalValue(), contribution.path("coefficientHigh").decimalValue(),
                    "SOURCE_ASSERTION", contribution.path("sideRule").asText(), sql(now), actor);
            jdbc.update("INSERT INTO exercise_catalog.exercise_contribution_evidence(id,contribution_id,evidence_source_id) VALUES (?,?,?)",
                    UUID.randomUUID(), contributionId, evidenceId);
        }
        jdbc.update("INSERT INTO exercise_catalog.exercise_evidence_link(id,exercise_version_id,evidence_source_id,claim_type,json_pointer) VALUES (?,?,?,'ANATOMY_EXPOSURE',?)",
                UUID.randomUUID(), versionId, evidenceId, "/contributions");
    }

    private List<Candidate> candidates(RecordData record) {
        try {
            JsonNode data = json.readTree(record.normalizedPayload);
            String name = normalizedAlias(data.path("name").asText());
            List<Candidate> result = new ArrayList<>();
            jdbc.query("""
                    SELECT DISTINCT exercise.id, lower(exercise.canonical_name),
                           EXISTS(SELECT 1 FROM exercise_catalog.exercise_alias alias
                                  WHERE alias.exercise_id=exercise.id AND alias.locale=? AND alias.normalized_alias=?) AS alias_match,
                           EXISTS(SELECT 1 FROM exercise_catalog.exercise_version version
                                  JOIN exercise_catalog.exercise_version_movement_pattern pattern ON pattern.exercise_version_id=version.id
                                  WHERE version.exercise_id=exercise.id AND pattern.movement_pattern=?) AS pattern_match,
                           EXISTS(SELECT 1 FROM exercise_catalog.exercise_version version
                                  JOIN exercise_catalog.exercise_movement_characteristic characteristic ON characteristic.exercise_version_id=version.id
                                  WHERE version.exercise_id=exercise.id AND characteristic.position_code=?) AS position_match,
                           EXISTS(SELECT 1 FROM exercise_catalog.exercise_version version
                                  JOIN exercise_catalog.exercise_movement_characteristic characteristic ON characteristic.exercise_version_id=version.id
                                  WHERE version.exercise_id=exercise.id AND characteristic.unilateral=?) AS unilateral_match,
                           EXISTS(SELECT 1 FROM exercise_catalog.exercise_version version
                                  JOIN exercise_catalog.exercise_movement_characteristic characteristic ON characteristic.exercise_version_id=version.id
                                  WHERE version.exercise_id=exercise.id AND characteristic.load_nature=?) AS load_nature_match,
                           CASE WHEN ?='' THEN EXISTS(SELECT 1 FROM exercise_catalog.exercise_version version
                                  WHERE version.exercise_id=exercise.id AND NOT EXISTS(SELECT 1 FROM exercise_catalog.exercise_equipment equipment WHERE equipment.exercise_version_id=version.id))
                                ELSE EXISTS(SELECT 1 FROM exercise_catalog.exercise_version version
                                  JOIN exercise_catalog.exercise_equipment equipment ON equipment.exercise_version_id=version.id
                                  WHERE version.exercise_id=exercise.id AND equipment.equipment_code=?) END AS equipment_match
                      FROM exercise_catalog.exercise exercise
                     WHERE lower(exercise.canonical_name)=? OR EXISTS(
                           SELECT 1 FROM exercise_catalog.exercise_alias alias
                            WHERE alias.exercise_id=exercise.id AND alias.locale=? AND alias.normalized_alias=?)
                     ORDER BY exercise.id
                    """, rs -> {
                        while (rs.next()) {
                            double score = .50 + (rs.getBoolean(3) ? .10 : 0) + (rs.getBoolean(4) ? .10 : 0)
                                    + (rs.getBoolean(5) ? .075 : 0) + (rs.getBoolean(6) ? .075 : 0)
                                    + (rs.getBoolean(7) ? .075 : 0) + (rs.getBoolean(8) ? .075 : 0);
                            String reasons = "{\"name\":\"exact\",\"alias\":" + rs.getBoolean(3)
                                    + ",\"movementPattern\":" + rs.getBoolean(4)
                                    + ",\"position\":" + rs.getBoolean(5)
                                    + ",\"unilateral\":" + rs.getBoolean(6)
                                    + ",\"loadNature\":" + rs.getBoolean(7)
                                    + ",\"equipment\":" + rs.getBoolean(8) + "}";
                            result.add(new Candidate(rs.getObject(1, UUID.class), Math.min(score, .99), reasons));
                        }
                    }, data.path("locale").asText(), name, data.path("movementPatterns").get(0).asText(),
                    data.path("position").asText(), data.path("unilateral").asBoolean(),
                    data.path("loadNature").asText(), data.path("equipment").isEmpty() ? "" : data.path("equipment").get(0).asText(),
                    data.path("equipment").isEmpty() ? "" : data.path("equipment").get(0).asText(),
                    name, data.path("locale").asText(), name);
            jdbc.query("""
                    SELECT DISTINCT ref.exercise_id FROM exercise_import.import_source_reference ref
                     WHERE ref.normalized_sha256=? AND ref.source_id<>? ORDER BY ref.exercise_id
                    """, rs -> {
                        while (rs.next()) {
                            UUID id = rs.getObject(1, UUID.class);
                            if (result.stream().noneMatch(item -> item.exerciseId.equals(id)))
                                result.add(new Candidate(id, 1.0, "{\"semanticChecksum\":\"identical\",\"crossSource\":true}"));
                        }
                    }, record.normalizedSha256, record.sourceId);
            return result;
        } catch (Exception impossible) {
            throw new IllegalStateException("stored normalized payload is invalid", impossible);
        }
    }

    private ArrayNode dictionaryArray(RecordData record, JsonNode source, String type) {
        ArrayNode result = json.createArrayNode();
        if (!source.isArray()) return result;
        StreamSupport.stream(source.spliterator(), false).map(JsonNode::asText).map(ImportRecordUseCases::upper)
                .distinct().sorted().map(value -> dictionaryValue(record, value, type)).forEach(result::add);
        return result;
    }

    private String dictionaryValue(RecordData record, String input, String type) {
        String value = upper(input);
        String table = switch (type) {
            case "EQUIPMENT" -> "exercise_catalog.exercise_equipment_dictionary";
            case "POSITION" -> "exercise_catalog.exercise_position_dictionary";
            case "DOSE_UNIT" -> "exercise_catalog.dose_unit_dictionary";
            default -> throw new IllegalArgumentException("unsupported dictionary");
        };
        Boolean canonical = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM " + table + " WHERE code=? AND active)",
                Boolean.class, value);
        if (Boolean.TRUE.equals(canonical)) return value;
        String mapped = jdbc.query("""
                SELECT canonical_value FROM exercise_import.import_mapping
                 WHERE source_id=? AND dictionary_type=? AND source_value=? AND status='APPROVED'
                """, rs -> rs.next() ? rs.getString(1) : null, record.sourceId, type, value);
        if (mapped != null) return mapped;
        UUID mappingId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO exercise_import.import_mapping(id,source_id,dictionary_type,source_value,status,created_at,version)
                VALUES (?,?,?,?,'PENDING',?,0) ON CONFLICT(source_id,dictionary_type,source_value) DO NOTHING
                """, mappingId, record.sourceId, type, value, sql(clock.instant()));
        issue(record, "MAPPING_REQUIRED", "NORMALIZE", "ERROR", "/" + type.toLowerCase(Locale.ROOT),
                "Wartość '" + value + "' wymaga zatwierdzonego mapowania " + type + ".");
        return value;
    }

    private ArrayNode normalizeDose(RecordData record, JsonNode source) {
        if (!source.isArray()) throw new RecordProblem("DOSE_REQUIRED", "/doseCapabilities", "Wymagana jest lista sposobów dawkowania.");
        List<ObjectNode> values = new ArrayList<>();
        for (JsonNode item : source) {
            ObjectNode value = json.createObjectNode();
            value.put("unit", dictionaryValue(record, item.path("unit").asText(), "DOSE_UNIT"));
            if (item.hasNonNull("minimum")) value.put("minimum", item.path("minimum").decimalValue());
            if (item.hasNonNull("maximum")) value.put("maximum", item.path("maximum").decimalValue());
            values.add(value);
        }
        values.sort(Comparator.comparing(value -> value.path("unit").asText()));
        ArrayNode result = json.createArrayNode(); values.forEach(result::add); return result;
    }

    private ArrayNode normalizeLoad(JsonNode source) {
        if (!source.isArray()) throw new RecordProblem("LOAD_PROFILE_REQUIRED", "/loadCharacteristics", "Wymagany jest profil obciążenia.");
        List<ObjectNode> values = new ArrayList<>();
        for (JsonNode item : source) {
            ObjectNode value = json.createObjectNode();
            value.put("movementPlane", upper(item.path("movementPlane").asText()));
            value.put("contractionType", upper(item.path("contractionType").asText()));
            value.put("rangeOfMotion", upper(item.path("rangeOfMotion").asText()));
            value.put("characteristicType", upper(item.path("characteristicType").asText()));
            values.add(value);
        }
        values.sort(Comparator.comparing(ObjectNode::toString));
        ArrayNode result = json.createArrayNode(); values.forEach(result::add); return result;
    }

    private ArrayNode normalizeContributions(RecordData record, JsonNode source) {
        if (!source.isArray()) throw new RecordProblem("ANATOMY_REQUIRED", "/contributions", "Wymagane są dane anatomii i ekspozycji.");
        List<ObjectNode> values = new ArrayList<>();
        for (JsonNode item : source) {
            ObjectNode value = json.createObjectNode();
            String anatomyCode = upper(item.path("anatomyCode").asText());
            Boolean exists = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM anatomy_reference.anatomical_structure WHERE code=? AND status='PUBLISHED')",
                    Boolean.class, anatomyCode);
            if (!Boolean.TRUE.equals(exists)) throw new RecordProblem("UNKNOWN_ANATOMY", "/contributions/anatomyCode", "Nieznana opublikowana anatomia: " + anatomyCode);
            value.put("anatomyCode", anatomyCode);
            value.put("role", upper(item.path("role").asText("PRIMARY")));
            value.put("loadChannel", upper(item.path("loadChannel").asText("DYN_EXU")));
            value.put("band", upper(item.path("band").asText("MODERATE")));
            value.put("coefficientLow", item.path("coefficientLow").decimalValue());
            value.put("coefficientHigh", item.path("coefficientHigh").decimalValue());
            value.put("sideRule", upper(item.path("sideRule").asText("AS_PRESCRIBED")));
            values.add(value);
        }
        values.sort(Comparator.comparing(ObjectNode::toString));
        ArrayNode result = json.createArrayNode(); values.forEach(result::add); return result;
    }

    private ArrayNode normalizedTextArray(JsonNode source, boolean sorted) {
        ArrayNode result = json.createArrayNode();
        if (!source.isArray()) return result;
        List<String> values = StreamSupport.stream(source.spliterator(), false).map(JsonNode::asText)
                .map(ImportRecordUseCases::clean).filter(value -> !value.isBlank()).distinct().toList();
        if (sorted) values = values.stream().sorted().toList();
        values.forEach(result::add); return result;
    }

    private ArrayNode normalizedTextArrayRequired(JsonNode source) {
        ArrayNode result = normalizedTextArray(source, false);
        if (result.isEmpty()) throw new RecordProblem("INSTRUCTIONS_REQUIRED", "/instructions", "Wymagana jest co najmniej jedna instrukcja.");
        return result;
    }

    private ArrayNode normalizedEnumArray(JsonNode source, Set<String> allowed, String pointer) {
        if (!source.isArray()) throw new RecordProblem("INVALID_ENUM_LIST", pointer, "Wymagana jest niepusta lista kodów.");
        List<String> values = StreamSupport.stream(source.spliterator(), false).map(JsonNode::asText)
                .map(ImportRecordUseCases::upper).distinct().sorted().toList();
        if (values.isEmpty() || !allowed.containsAll(values)) throw new RecordProblem("INVALID_ENUM_VALUE", pointer, "Lista zawiera nieobsługiwany kod.");
        ArrayNode result = json.createArrayNode(); values.forEach(result::add); return result;
    }

    private static String enumValue(JsonNode root, String field, Set<String> allowed) {
        String value = upper(root.path(field).asText());
        if (!allowed.contains(value)) throw new RecordProblem("INVALID_ENUM_VALUE", "/" + field, "Nieobsługiwana wartość " + field + ".");
        return value;
    }

    private void requireContract(JsonNode raw) {
        if (!raw.isObject()) throw new RecordProblem("RECORD_NOT_OBJECT", "/", "Rekord JSONL musi być obiektem.");
        if (!"moves.exercise-import/1.0".equals(raw.path("schemaVersion").asText()))
            throw new RecordProblem("UNSUPPORTED_SCHEMA_VERSION", "/schemaVersion", "Obsługiwany kontrakt to moves.exercise-import/1.0.");
    }

    private static String requiredText(JsonNode root, String field, int maximum) {
        String value = root.path(field).asText();
        if (value.isBlank() || value.length() > maximum)
            throw new RecordProblem("REQUIRED_TEXT", "/" + field, "Pole " + field + " jest wymagane i ma limit " + maximum + " znaków.");
        return value;
    }

    private static void requireNonEmpty(JsonNode value, String pointer, String code) {
        if (!value.isArray() || value.isEmpty()) throw new RecordProblem(code, pointer, "Wymagana lista jest pusta.");
    }

    private boolean unresolvedMappings(UUID recordId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM exercise_import.import_issue WHERE record_id=? AND code='MAPPING_REQUIRED' AND resolved_at IS NULL",
                Integer.class, recordId);
        return count != null && count > 0;
    }

    private RecordData record(UUID id, boolean lock) {
        String suffix = lock ? " FOR UPDATE OF record" : "";
        RecordData record = jdbc.query("""
                SELECT record.id,record.batch_id,batch.source_id,record.row_number,record.source_record_key,
                       record.status,record.raw_payload::text,record.normalized_payload::text,
                       record.normalized_sha256,record.matched_exercise_id,record.draft_version_id,
                       source.default_locale,source.license_verified
                  FROM exercise_import.import_record record
                  JOIN exercise_import.import_batch batch ON batch.id=record.batch_id
                  JOIN exercise_import.import_source source ON source.id=batch.source_id
                 WHERE record.id=?
                """ + suffix, rs -> rs.next() ? new RecordData(
                rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                rs.getLong(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                rs.getString(9), rs.getObject(10, UUID.class), rs.getObject(11, UUID.class),
                rs.getString(12), rs.getBoolean(13)) : null, id);
        if (record == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "import record not found");
        return record;
    }

    private void setStatus(UUID recordId, String status) {
        jdbc.update("UPDATE exercise_import.import_record SET status=?,updated_at=?,version=version+1 WHERE id=?",
                status, sql(clock.instant()), recordId);
    }

    private void invalidate(RecordData record, String stage, RecordProblem problem) {
        invalidate(record, problem.code, stage, problem.pointer, problem.getMessage());
    }

    private void invalidate(RecordData record, String code, String stage, String pointer, String message) {
        issue(record, code, stage, "ERROR", pointer, message);
        setStatus(record.id, "INVALID");
    }

    private void issue(RecordData record, String code, String stage, String severity, String pointer, String message) {
        jdbc.update("""
                INSERT INTO exercise_import.import_issue(
                    id,batch_id,record_id,row_number,code,stage,severity,json_pointer,message,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING
                """, UUID.randomUUID(), record.batchId, record.id, record.rowNumber, code, stage,
                severity, pointer, message, sql(clock.instant()));
    }

    private static String normalizeLocale(String value) {
        Locale locale = Locale.forLanguageTag(value.replace('_', '-'));
        if (locale.getLanguage().isBlank()) throw new RecordProblem("INVALID_LOCALE", "/locale", "Niepoprawny locale.");
        return locale.toLanguageTag();
    }

    private static String clean(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .trim().replaceAll("\\s+", " ");
    }

    private static String upper(String value) {
        return clean(value).toUpperCase(Locale.ROOT);
    }

    private static String normalizedAlias(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static List<String> sortedStrings(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).sorted().toList();
    }

    private static List<String> sortedByInput(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }

    private static java.math.BigDecimal decimalText(JsonNode value) {
        return value.isMissingNode() || value.isNull() ? null : value.decimalValue();
    }

    private static Timestamp sql(Instant value) { return Timestamp.from(value); }

    private record RecordData(UUID id, UUID batchId, UUID sourceId, long rowNumber,
                              String sourceRecordKey, String status, String rawPayload,
                              String normalizedPayload, String normalizedSha256,
                              UUID matchedExerciseId, UUID draftVersionId,
                              String defaultLocale, boolean licenseVerified) {
    }
    private record SourceReference(UUID exerciseId, String hash) {
    }
    private record Candidate(UUID exerciseId, double score, String reasons) {
    }

    private static final class RecordProblem extends RuntimeException {
        final String code;
        final String pointer;
        RecordProblem(String code, String pointer, String message) {
            super(message); this.code = code; this.pointer = pointer;
        }
    }
}
