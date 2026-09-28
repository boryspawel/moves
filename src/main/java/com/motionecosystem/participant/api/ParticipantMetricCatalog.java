package com.motionecosystem.participant.api;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Code-owned participant metric vocabulary shared by facts and goal presets. */
public final class ParticipantMetricCatalog {
    public enum PresetId { BODY_WEIGHT, BODY_CIRCUMFERENCE, MAX_LOAD, DISTANCE, COMPLETION_TIME, HOLD_DURATION, CUSTOM }
    public record ParticipantMeasurementPresetView(String id, String label, List<String> requiredContextFields, List<String> allowedUnits, String defaultUnit) { }
    public record Derived(String metricCode, String unit, String measurementMethod, String title) { }
    private record Definition(String label, List<String> required, List<String> units, String method) { }
    private static final Map<PresetId, Definition> DEFINITIONS = Map.of(
            PresetId.BODY_WEIGHT, new Definition("Masa ciała", List.of(), List.of("kg"), "body-weight"),
            PresetId.BODY_CIRCUMFERENCE, new Definition("Obwód ciała", List.of("BODY_AREA"), List.of("cm"), "body-circumference"),
            PresetId.MAX_LOAD, new Definition("Maksymalny ciężar", List.of("EXERCISE"), List.of("kg"), "max-load"),
            PresetId.DISTANCE, new Definition("Dystans", List.of("ACTIVITY"), List.of("m", "km"), "distance"),
            PresetId.COMPLETION_TIME, new Definition("Czas wykonania", List.of("ACTIVITY"), List.of("s"), "completion-time"),
            PresetId.HOLD_DURATION, new Definition("Czas utrzymania", List.of("EXERCISE"), List.of("s"), "hold-duration"),
            PresetId.CUSTOM, new Definition("Własna miara", List.of("CUSTOM_LABEL"), List.of(), "custom"));
    private ParticipantMetricCatalog() { }
    public static List<ParticipantMeasurementPresetView> views() { return java.util.Arrays.stream(PresetId.values()).map(id -> { var d = DEFINITIONS.get(id); return new ParticipantMeasurementPresetView(id.name(), d.label, d.required, d.units, d.units.isEmpty() ? null : d.units.getFirst()); }).toList(); }
    public static Derived derive(PresetId id, String bodyArea, String customLabel, String exercise, String activity, String requestedUnit) {
        if (id == null) bad("presetId is required"); var d = DEFINITIONS.get(id);
        String qualifier = switch (id) { case BODY_CIRCUMFERENCE -> required(bodyArea, "bodyArea"); case MAX_LOAD, HOLD_DURATION -> required(exercise, "exercise"); case DISTANCE, COMPLETION_TIME -> required(activity, "activity"); case CUSTOM -> required(customLabel, "customLabel"); default -> ""; };
        if (id == PresetId.BODY_CIRCUMFERENCE && !Set.of("waist", "hips", "chest", "arm", "thigh", "calf", "neck", "other").contains(bodyArea)) bad("bodyArea is invalid");
        String unit = id == PresetId.CUSTOM ? required(requestedUnit, "unit") : requestedUnit == null || requestedUnit.isBlank() ? d.units.getFirst() : requestedUnit.trim();
        if (id != PresetId.CUSTOM && !d.units.contains(unit)) bad("unit is not allowed for preset");
        // Goal presets historically retain the qualifier verbatim except for upper-casing.
        // Keep that canonical representation while centralising its derivation here.
        String metric = id == PresetId.CUSTOM ? "CUSTOM:" + normalized(qualifier)
                : d.method + (qualifier.isBlank() ? "" : ":" + qualifier.trim().toUpperCase(Locale.ROOT));
        return new Derived(metric, unit, d.method, d.label + (qualifier.isBlank() ? "" : ": " + qualifier));
    }
    /** Human-facing Polish label for a persisted canonical metric code. */
    public static String labelForMetricCode(String metricCode) {
        if (metricCode == null || metricCode.isBlank()) return "Pomiar";
        for (PresetId id : PresetId.values()) {
            Definition definition = DEFINITIONS.get(id);
            String prefix = id == PresetId.CUSTOM ? "CUSTOM:" : definition.method + ":";
            if (metricCode.equals(definition.method)) return definition.label;
            if (metricCode.startsWith(prefix)) {
                String qualifier = displayQualifier(id, metricCode.substring(prefix.length()));
                return qualifier.isBlank() ? definition.label : definition.label + ": " + qualifier;
            }
        }
        return "Pomiar";
    }
    private static String displayQualifier(PresetId id, String value) {
        String qualifier = value.replace('_', ' ').toLowerCase(Locale.ROOT);
        if (id != PresetId.BODY_CIRCUMFERENCE) return qualifier;
        return switch (qualifier) {
            case "waist" -> "talia";
            case "hips" -> "biodra";
            case "chest" -> "klatka piersiowa";
            case "arm" -> "ramię";
            case "thigh" -> "udo";
            case "calf" -> "łydka";
            case "neck" -> "szyja";
            case "other" -> "inne";
            default -> qualifier;
        };
    }
    private static String normalized(String value) { return value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_"); }
    private static String required(String value, String field) { if (value == null || value.isBlank() || value.trim().length() > 120) bad(field + " is required and must be at most 120 characters"); return value.trim(); }
    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
