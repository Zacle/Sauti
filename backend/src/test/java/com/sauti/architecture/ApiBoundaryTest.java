package com.sauti.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class ApiBoundaryTest {
    @Test
    void controllersDoNotImportRepositories() throws Exception {
        var apiSource = sourceDirectory();
        var violations = new ArrayList<String>();
        try (var files = Files.walk(apiSource)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    Files.readAllLines(path).stream()
                            .filter(line -> line.matches("\\s*import com\\.sauti\\..*Repository;\\s*"))
                            .forEach(line -> violations.add(path.getFileName() + ": " + line.trim()));
                } catch (Exception exception) {
                    throw new IllegalStateException("Could not inspect " + path, exception);
                }
            });
        }
        assertThat(violations)
                .as("API adapters must call feature services instead of repositories")
                .isEmpty();
    }

    private Path sourceDirectory() {
        var moduleRelative = Path.of("src", "main", "java", "com", "sauti", "api");
        if (Files.isDirectory(moduleRelative)) return moduleRelative;
        var rootRelative = Path.of("backend", "src", "main", "java", "com", "sauti", "api");
        if (Files.isDirectory(rootRelative)) return rootRelative;
        throw new IllegalStateException("Could not locate backend API sources");
    }
}
