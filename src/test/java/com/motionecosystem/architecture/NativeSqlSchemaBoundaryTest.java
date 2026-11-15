package com.motionecosystem.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Source-level complement to ArchUnit: dependencies cannot reveal SQL table names
 * embedded in native JPA query strings.
 */
class NativeSqlSchemaBoundaryTest {
    private static final Path SOURCES = Path.of("src/main/java/com/motionecosystem");
    private static final Pattern TABLE_REFERENCE = Pattern.compile(
            "(?i)\\b(?:from|join|update|into)\\s+([a-z][a-z0-9_]*)\\.([a-z][a-z0-9_]*)\\b");

    // Existing violation: remove this exception when search uses the anatomy module's read port.
    private static final Set<String> LEGACY_EXCEPTIONS = Set.of(
            "exercisecatalog/ExerciseCatalogSearchService.java -> anatomy_reference.anatomical_structure");

    @Test
    void nativeSqlDoesNotIntroduceNewCrossModuleTableReads() throws IOException {
        assertThat(Files.isDirectory(SOURCES)).isTrue();
        SortedSet<String> actual = new TreeSet<>();
        try (var paths = Files.walk(SOURCES)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                String modulePath = SOURCES.relativize(path).toString().replace('\\', '/');
                String source = Files.readString(path);
                if (hasNativeSql(source)) {
                    actual.addAll(crossModuleReferences(modulePath, source));
                }
            }
        }
        assertThat(actual)
                .as("Cross-module SQL references must be removed or explicitly documented as legacy")
                .containsExactlyInAnyOrderElementsOf(LEGACY_EXCEPTIONS);
    }

    @Test
    void detectsForeignTablesButAllowsNativeSqlAgainstOwnSchema() {
        String sql = "SELECT * FROM exercise_catalog.exercise e "
                + "JOIN anatomy_reference.anatomical_structure a ON a.id = e.id";
        assertThat(crossModuleReferences("exercisecatalog/Synthetic.java", sql))
                .containsExactly("exercisecatalog/Synthetic.java -> anatomy_reference.anatomical_structure");
        assertThat(crossModuleReferences("exercisecatalog/Synthetic.java",
                "UPDATE exercise_catalog.exercise SET status = 'PUBLISHED'")).isEmpty();
    }

    private static boolean hasNativeSql(String source) {
        return source.contains("createNativeQuery(")
                || source.contains("@NamedNativeQuery(")
                || Pattern.compile("\\bnativeQuery\\s*=\\s*true\\b").matcher(source).find();
    }

    private static SortedSet<String> crossModuleReferences(String modulePath, String source) {
        String owner = modulePath.substring(0, modulePath.indexOf('/'));
        SortedSet<String> references = new TreeSet<>();
        var matcher = TABLE_REFERENCE.matcher(source);
        while (matcher.find()) {
            String schema = matcher.group(1).toLowerCase(java.util.Locale.ROOT);
            if (!schema.replace("_", "").equals(owner)
                    && !Set.of("pg_catalog", "information_schema").contains(schema)) {
                references.add(modulePath + " -> " + schema + "." + matcher.group(2).toLowerCase(java.util.Locale.ROOT));
            }
        }
        return references;
    }
}
