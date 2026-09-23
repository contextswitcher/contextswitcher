package com.contextswitcher.provenance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.contextswitcher.tasks.TextFiles;

/// Where provenance records go: `provenance.yaml` at the task directory's root
/// names the provenance repository (`repo: <clone URL>`), so every machine
/// syncing the tasks finds it. Not Markdown, so the task scanner ignores it.
// [impl->dsn~provenance-repository~1]
public final class ProvenanceConfig {

    public static final String FILE_NAME = "provenance.yaml";

    private ProvenanceConfig() {
    }

    /// The provenance repository's clone URL, or null when the file is missing,
    /// names none, or cannot be read — then nothing is recorded.
    public static @Nullable String repository(Path tasksDir) {
        Path file = tasksDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(TextFiles.read(file));
            if (loaded instanceof Map<?, ?> map && map.get("repo") instanceof String repo && !repo.isBlank()) {
                return repo.strip();
            }
        } catch (Exception e) {
            org.tinylog.Logger.warn("Cannot read {}: {}", file, e.toString());
        }
        return null;
    }
}
