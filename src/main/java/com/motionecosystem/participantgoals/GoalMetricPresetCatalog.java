package com.motionecosystem.participantgoals;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Versioned, code-owned goal metric definitions. Goals retain derived outcome snapshots, never catalog references. */
final class GoalMetricPresetCatalog {
    static final String VERSION = "1";
    enum PresetId { BODY_WEIGHT, BODY_CIRCUMFERENCE, MAX_LOAD, DISTANCE, COMPLETION_TIME, HOLD_DURATION, CUSTOM }
    enum ContextField { BODY_AREA, CUSTOM_LABEL, EXERCISE, ACTIVITY }
    record PresetView(String id, String label, String description, List<String> requiredContextFields, List<String> allowedUnits,
                      String defaultComparator, boolean comparatorSelectable, boolean baselineSupported) { }
    record Derived(String metricCode, String unit, String measurementMethod, TargetComparator comparator, String title) { }

    private static final Map<PresetId, Definition> DEFINITIONS = Map.of(
            PresetId.BODY_WEIGHT, new Definition("Masa ciała", "Masa ciała", List.of(), List.of("kg"), TargetComparator.AT_MOST, false, "body-weight"),
            PresetId.BODY_CIRCUMFERENCE, new Definition("Obwód ciała", "Obwód", List.of(ContextField.BODY_AREA), List.of("cm"), TargetComparator.AT_MOST, false, "body-circumference"),
            PresetId.MAX_LOAD, new Definition("Maksymalny ciężar", "Maksymalny ciężar", List.of(ContextField.EXERCISE), List.of("kg"), TargetComparator.AT_LEAST, false, "max-load"),
            PresetId.DISTANCE, new Definition("Dystans", "Dystans", List.of(ContextField.ACTIVITY), List.of("m", "km"), TargetComparator.AT_LEAST, false, "distance"),
            PresetId.COMPLETION_TIME, new Definition("Czas wykonania", "Czas", List.of(ContextField.ACTIVITY), List.of("s"), TargetComparator.AT_MOST, false, "completion-time"),
            PresetId.HOLD_DURATION, new Definition("Czas utrzymania", "Czas utrzymania", List.of(ContextField.EXERCISE), List.of("s"), TargetComparator.AT_LEAST, false, "hold-duration"),
            PresetId.CUSTOM, new Definition("Własna miara", "Cel", List.of(ContextField.CUSTOM_LABEL), List.of(), TargetComparator.AT_LEAST, true, "custom")
    );
    private GoalMetricPresetCatalog() { }
    static List<PresetView> views() { return Arrays.stream(PresetId.values()).map(GoalMetricPresetCatalog::view).toList(); }
    static PresetView view(PresetId id) { Definition d = definition(id); return new PresetView(id.name(), d.label, "Katalog celów " + VERSION, d.required.stream().map(Enum::name).toList(), d.units, d.comparator.name(), d.flexible, true); }
    static Derived derive(PresetId id, String bodyArea, String customLabel, String exercise, String activity, String requestedUnit, TargetComparator requestedComparator) {
        Definition d = definition(id);
        for (ContextField field : d.required) required(contextValue(field, bodyArea, customLabel, exercise, activity), field.name().toLowerCase());
        if (id == PresetId.BODY_CIRCUMFERENCE && !Set.of("waist", "hips", "chest", "arm", "thigh", "calf", "neck", "other").contains(bodyArea)) bad("bodyArea is invalid");
        String unit = id == PresetId.CUSTOM ? required(requestedUnit, "unit") : requestedUnit == null || requestedUnit.isBlank() ? d.units.get(0) : requestedUnit.trim();
        if (!id.equals(PresetId.CUSTOM) && !d.units.contains(unit)) bad("unit is not allowed for preset");
        if (d.flexible && requestedComparator == null) bad("targetComparator is required");
        TargetComparator comparator = d.flexible ? requestedComparator : d.comparator;
        String qualifier = switch (id) { case BODY_CIRCUMFERENCE -> bodyArea; case MAX_LOAD, HOLD_DURATION -> exercise; case DISTANCE, COMPLETION_TIME -> activity; case CUSTOM -> customLabel; default -> ""; };
        String metric = id == PresetId.CUSTOM ? "CUSTOM:" + qualifier.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_") : d.method + (qualifier.isBlank() ? "" : ":" + qualifier.trim().toUpperCase(Locale.ROOT));
        return new Derived(metric, unit, d.method, comparator, d.title + (qualifier.isBlank() ? "" : ": " + qualifier.trim()));
    }
    private static Definition definition(PresetId id) { if (id == null) bad("presetId is required"); return DEFINITIONS.get(id); }
    private static String contextValue(ContextField field, String bodyArea, String customLabel, String exercise, String activity) { return switch (field) { case BODY_AREA -> bodyArea; case CUSTOM_LABEL -> customLabel; case EXERCISE -> exercise; case ACTIVITY -> activity; }; }
    private static String required(String value, String field) { if (value == null || value.isBlank() || value.trim().length() > 120) bad(field + " is required and must be at most 120 characters"); return value.trim(); }
    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private record Definition(String label, String title, List<ContextField> required, List<String> units, TargetComparator comparator, boolean flexible, String method) { }
}
