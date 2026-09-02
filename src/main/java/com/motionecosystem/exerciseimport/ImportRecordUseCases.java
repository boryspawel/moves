package com.motionecosystem.exerciseimport;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.exercisecatalog.api.ImportedExerciseDraftPort;
import com.motionecosystem.exerciseimport.api.CreateExerciseDraft;
import com.motionecosystem.exerciseimport.api.FindExerciseMatch;
import com.motionecosystem.exerciseimport.api.NormalizeImportRecord;
import com.motionecosystem.exerciseimport.api.ValidateImportRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
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

    private final ExerciseImportRecordRepository records;
    private final ExerciseImportBatchRepository batches;
    private final ExerciseImportSourceRepository sources;
    private final ExerciseImportSourceReferenceRepository sourceReferences;
    private final ExerciseImportMappingRepository mappings;
    private final ExerciseImportIssueRepository issues;
    private final ExerciseImportMatchCandidateRepository candidates;
    private final ImportCatalogReadRepository catalog;
    private final ImportedExerciseDraftPort drafts;
    private final ObjectMapper json;
    private final Clock clock;
    private final AuditRecorder audit;

    @Override
    @Transactional
    public void normalize(UUID recordId) {
        RecordData record = record(recordId, true);
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
            ExerciseImportRecordEntity entity = lockedRecord(recordId);
            entity.sourceRecordKey = normalized.path("sourceRecordKey").asText();
            entity.normalizedPayload = canonical;
            entity.normalizedSha256 = hash;
            entity.normalizationVersion = NORMALIZATION_VERSION;
            entity.status = blocked ? "BLOCKED_BY_MAPPING" : "NORMALIZED";
            entity.updatedAt = clock.instant();
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
        RecordData record = record(recordId, true);
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
        ExerciseImportSourceReferenceEntity reference = sourceReferences
                .findBySourceIdAndSourceRecordKey(record.sourceId, record.sourceRecordKey).orElse(null);
        if (reference != null) {
            ExerciseImportRecordEntity entity = lockedRecord(recordId);
            if (reference.normalizedSha256.equals(record.normalizedSha256)) {
                entity.status = "UNCHANGED";
                entity.matchedExerciseId = reference.exerciseId;
                entity.updatedAt = clock.instant();
                reference.lastRecordId = recordId;
                reference.updatedAt = clock.instant();
            } else {
                entity.status = "READY_FOR_DRAFT";
                entity.matchedExerciseId = reference.exerciseId;
                entity.updatedAt = clock.instant();
            }
            return;
        }

        // A verified source record key is its authoritative identity.  Similarity is
        // deliberately reserved for records without that stable identity: presenting
        // name-based candidates here would turn an unambiguous import into editorial
        // work and could attach a new source record to the wrong exercise.
        if (record.licenseVerified && record.sourceRecordKey != null && !record.sourceRecordKey.isBlank()) {
            candidates.deleteByRecordId(recordId);
            setStatus(recordId, "READY_FOR_DRAFT");
            return;
        }

        candidates.deleteByRecordId(recordId);
        List<Candidate> found = candidates(record);
        int rank = 1;
        for (Candidate candidate : found.stream().sorted(Comparator
                .comparingDouble(Candidate::score).reversed().thenComparing(Candidate::exerciseId)).limit(5).toList()) {
            ExerciseImportMatchCandidateEntity entity = new ExerciseImportMatchCandidateEntity();
            entity.id = UUID.randomUUID(); entity.recordId = recordId; entity.exerciseId = candidate.exerciseId;
            entity.rank = rank++; entity.score = java.math.BigDecimal.valueOf(candidate.score);
            entity.reasons = candidate.reasons; entity.algorithmVersion = MATCH_VERSION;
            candidates.save(entity);
        }
        setStatus(recordId, found.isEmpty() ? "READY_FOR_DRAFT" : "MATCH_CANDIDATES");
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
            UUID versionId = drafts.create(draftCommand(record, data, actorSubject));
            ExerciseImportRecordEntity entity = lockedRecord(recordId);
            entity.status = "DRAFTED"; entity.draftVersionId = versionId;
            entity.matchedExerciseId = entity.matchedExerciseId == null ? exerciseIdFor(versionId) : entity.matchedExerciseId;
            entity.updatedAt = now;
            issues.findByRecordIdAndCodeAndResolvedAtIsNull(recordId, "DRAFT_CREATION_FAILED")
                    .forEach(issue -> issue.resolvedAt = now);
            ExerciseImportSourceReferenceEntity reference = sourceReferences.findBySourceIdAndSourceRecordKey(record.sourceId, record.sourceRecordKey)
                    .orElseGet(ExerciseImportSourceReferenceEntity::new);
            boolean newReference = reference.id == null;
            if (newReference) { reference.id = UUID.randomUUID(); reference.sourceId = record.sourceId; reference.sourceRecordKey = record.sourceRecordKey; reference.firstRecordId = recordId; }
            reference.exerciseId = entity.matchedExerciseId; reference.latestExerciseVersionId = versionId;
            reference.normalizedSha256 = record.normalizedSha256; reference.lastRecordId = recordId; reference.updatedAt = now;
            sourceReferences.save(reference);
            audit.record(actorSubject, record.matchedExerciseId == null ? "IMPORTED_EXERCISE_DRAFT_CREATED" : "IMPORTED_EXERCISE_VERSION_DRAFT_CREATED",
                    "ExerciseVersion", versionId);
            return versionId;
        } catch (ResponseStatusException status) {
            throw status;
        } catch (Exception invalid) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "normalized record cannot create a draft", invalid);
        }
    }

    private ImportedExerciseDraftPort.ImportedExerciseDraft draftCommand(RecordData record, JsonNode data, String actor) {
        List<ImportedExerciseDraftPort.Contribution> contributions = new ArrayList<>();
        for (JsonNode value : data.path("contributions")) {
            UUID anatomy = catalog.publishedAnatomyId(value.path("anatomyCode").asText());
            if (anatomy == null) throw new RecordProblem("UNKNOWN_ANATOMY", "/contributions", "Nieznana opublikowana anatomia.");
            contributions.add(new ImportedExerciseDraftPort.Contribution(anatomy, value.path("role").asText(), value.path("loadChannel").asText(), value.path("band").asText(), value.path("coefficientLow").decimalValue(), value.path("coefficientHigh").decimalValue(), value.path("sideRule").asText()));
        }
        return new ImportedExerciseDraftPort.ImportedExerciseDraft(record.matchedExerciseId, record.id, record.sourceId,
                record.sourceLicenseCode, record.sourceRecordKey, actor,
                data.path("name").asText(), data.path("locale").asText(), record.normalizedSha256,
                String.join("\n", sortedByInput(data.path("instructions"))), sortedStrings(data.path("movementPatterns")).getFirst(),
                data.path("stimulusType").asText(), data.path("fatigueProfile").asText(), data.path("technicalLevel").asText(), data.path("environment").asText(),
                sortedStrings(data.path("purposes")), sortedStrings(data.path("aliases")), sortedStrings(data.path("movementPatterns")), sortedStrings(data.path("equipment")),
                steps(data), doses(data), characteristics(data), loads(data), contributions);
    }

    private static List<ImportedExerciseDraftPort.InstructionStep> steps(JsonNode data) {
        List<ImportedExerciseDraftPort.InstructionStep> result = new ArrayList<>(); int number = 1;
        for (String instruction : sortedByInput(data.path("instructions"))) result.add(new ImportedExerciseDraftPort.InstructionStep(number++, instruction));
        return result;
    }
    private static List<ImportedExerciseDraftPort.DoseCapability> doses(JsonNode data) {
        List<ImportedExerciseDraftPort.DoseCapability> result = new ArrayList<>();
        for (JsonNode value : data.path("doseCapabilities")) result.add(new ImportedExerciseDraftPort.DoseCapability(value.path("unit").asText(), decimalText(value.path("minimum")), decimalText(value.path("maximum"))));
        return result;
    }
    private static List<ImportedExerciseDraftPort.MovementCharacteristic> characteristics(JsonNode data) {
        return sortedStrings(data.path("movementPatterns")).stream().map(pattern -> new ImportedExerciseDraftPort.MovementCharacteristic(pattern, data.path("position").asText(), data.path("unilateral").asBoolean(), data.path("loadNature").asText())).toList();
    }
    private static List<ImportedExerciseDraftPort.LoadCharacteristic> loads(JsonNode data) {
        List<ImportedExerciseDraftPort.LoadCharacteristic> result = new ArrayList<>();
        for (JsonNode value : data.path("loadCharacteristics")) result.add(new ImportedExerciseDraftPort.LoadCharacteristic(value.path("movementPlane").asText(), value.path("contractionType").asText(), value.path("rangeOfMotion").asText(), value.path("characteristicType").asText()));
        return result;
    }

    private UUID exerciseIdFor(UUID versionId) { return catalog.exerciseIdForVersion(versionId); }

    private List<Candidate> candidates(RecordData record) {
        try {
            JsonNode data = json.readTree(record.normalizedPayload);
            String name = normalizedAlias(data.path("name").asText());
            List<Candidate> result = new ArrayList<>();
            for (UUID id : catalog.exactNameOrAlias(data.path("locale").asText(), name)) {
                boolean alias = catalog.aliasMatch(id, data.path("locale").asText(), name);
                boolean pattern = catalog.patternMatch(id, data.path("movementPatterns").get(0).asText());
                boolean position = catalog.positionMatch(id, data.path("position").asText());
                boolean unilateral = catalog.unilateralMatch(id, data.path("unilateral").asBoolean());
                boolean loadNature = catalog.loadNatureMatch(id, data.path("loadNature").asText());
                boolean equipment = catalog.equipmentMatch(id, data.path("equipment").isEmpty() ? "" : data.path("equipment").get(0).asText());
                double score = .50 + (alias ? .10 : 0) + (pattern ? .10 : 0) + (position ? .075 : 0)
                        + (unilateral ? .075 : 0) + (loadNature ? .075 : 0) + (equipment ? .075 : 0);
                result.add(new Candidate(id, Math.min(score, .99), "{\"name\":\"exact\",\"alias\":" + alias
                        + ",\"movementPattern\":" + pattern + ",\"position\":" + position + ",\"unilateral\":" + unilateral
                        + ",\"loadNature\":" + loadNature + ",\"equipment\":" + equipment + "}"));
            }
            for (ExerciseImportSourceReferenceEntity ref : sourceReferences.findByNormalizedSha256AndSourceIdNotOrderByExerciseId(record.normalizedSha256, record.sourceId)) {
                if (result.stream().noneMatch(item -> item.exerciseId.equals(ref.exerciseId)))
                    result.add(new Candidate(ref.exerciseId, 1.0, "{\"semanticChecksum\":\"identical\",\"crossSource\":true}"));
            }
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
        if (catalog.activeDictionaryContains(type, value)) return value;
        Optional<ExerciseImportMappingEntity> existing = mappings.findBySourceIdAndDictionaryTypeAndSourceValue(record.sourceId, type, value);
        String mapped = existing
                .filter(mapping -> "APPROVED".equals(mapping.status)).map(mapping -> mapping.canonicalValue).orElse(null);
        if (mapped != null) return mapped;
        if (existing.isEmpty()) {
            ExerciseImportMappingEntity mapping = new ExerciseImportMappingEntity();
            mapping.id = UUID.randomUUID(); mapping.sourceId = record.sourceId; mapping.dictionaryType = type;
            mapping.sourceValue = value; mapping.status = "PENDING"; mapping.createdAt = clock.instant(); mappings.save(mapping);
        }
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
            if (catalog.publishedAnatomyId(anatomyCode) == null) throw new RecordProblem("UNKNOWN_ANATOMY", "/contributions/anatomyCode", "Nieznana opublikowana anatomia: " + anatomyCode);
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
        return issues.existsByRecordIdAndCodeAndResolvedAtIsNull(recordId, "MAPPING_REQUIRED");
    }

    private RecordData record(UUID id, boolean lock) {
        ExerciseImportRecordEntity entity = lock ? lockedRecord(id) : records.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "import record not found"));
        ExerciseImportBatchEntity batch = batches.findById(entity.batchId).orElseThrow(() -> new IllegalStateException("import batch not found"));
        // A source is the common parent for mapping and source-reference natural keys.
        // Serializing writers on it keeps their check-then-insert operations idempotent
        // without swallowing unrelated database constraint violations.
        ExerciseImportSourceEntity source = (lock ? sources.findLockedById(batch.sourceId) : sources.findById(batch.sourceId))
                .orElseThrow(() -> new IllegalStateException("import source not found"));
        return new RecordData(entity.id, entity.batchId, source.id, entity.rowNumber, entity.sourceRecordKey, entity.status,
                entity.rawPayload, entity.normalizedPayload, entity.normalizedSha256, entity.matchedExerciseId, entity.draftVersionId,
                source.defaultLocale, source.licenseVerified, source.licenseCode);
    }

    private ExerciseImportRecordEntity lockedRecord(UUID id) {
        return records.findLockedById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "import record not found"));
    }

    private void setStatus(UUID recordId, String status) {
        ExerciseImportRecordEntity entity = lockedRecord(recordId); entity.status = status; entity.updatedAt = clock.instant();
    }

    private void invalidate(RecordData record, String stage, RecordProblem problem) {
        invalidate(record, problem.code, stage, problem.pointer, problem.getMessage());
    }

    private void invalidate(RecordData record, String code, String stage, String pointer, String message) {
        issue(record, code, stage, "ERROR", pointer, message);
        setStatus(record.id, "INVALID");
    }

    private void issue(RecordData record, String code, String stage, String severity, String pointer, String message) {
        if (!issues.existsByBatchIdAndRecordIdAndCodeAndJsonPointer(record.batchId, record.id, code, pointer)) {
            ExerciseImportIssueEntity issue = new ExerciseImportIssueEntity(); issue.id = UUID.randomUUID(); issue.batchId = record.batchId;
            issue.recordId = record.id; issue.rowNumber = record.rowNumber; issue.code = code; issue.stage = stage; issue.severity = severity;
            issue.jsonPointer = pointer; issue.message = message; issue.createdAt = clock.instant(); issues.save(issue);
        }
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

    private record RecordData(UUID id, UUID batchId, UUID sourceId, long rowNumber,
                              String sourceRecordKey, String status, String rawPayload,
                              String normalizedPayload, String normalizedSha256,
                              UUID matchedExerciseId, UUID draftVersionId,
                              String defaultLocale, boolean licenseVerified, String sourceLicenseCode) {
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
