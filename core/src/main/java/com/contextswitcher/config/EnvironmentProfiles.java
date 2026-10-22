package com.contextswitcher.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/// The `profiles:` mapping of `settings.yaml` — named [EnvironmentProfile]s,
/// one of which may be `default`, the system's normal look. A category picks
/// one with `profile:` in its `CONTEXTSWITCHER.md`; the status bar can lay a
/// manual override (e.g. `screenshare`) on top.
///
/// Read on its own rather than as an [AppSettings] field: it is consulted
/// fresh on every switch (an edited profile needs no restart), and the
/// settings editor patches the file key by key, so the section survives a
/// form save untouched.
// [impl->dsn~environment-profiles~1]
public record EnvironmentProfiles(Map<String, EnvironmentProfile> byName) {

    /// The profile every category without `profile:` gets, and the bottom
    /// layer under every other one.
    public static final String DEFAULT = "default";

    public static final EnvironmentProfiles NONE = new EnvironmentProfiles(Map.of());

    public EnvironmentProfiles {
        byName = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(byName));
    }

    /// The `profiles:` section of a `settings.yaml` text; [#NONE] when the
    /// file has none or does not parse (a half-edited settings file must not
    /// break a switch). A profile whose value is no mapping is skipped.
    public static EnvironmentProfiles parse(String settingsYaml) {
        Object loaded;
        try {
            loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(settingsYaml);
        } catch (RuntimeException e) {
            return NONE;
        }
        if (!(loaded instanceof Map<?, ?> root) || !(root.get("profiles") instanceof Map<?, ?> profiles)) {
            return NONE;
        }
        Map<String, EnvironmentProfile> byName = new LinkedHashMap<>();
        profiles.forEach((name, value) -> {
            if (name != null && value instanceof Map<?, ?> map) {
                byName.put(name.toString(), EnvironmentProfile.fromYaml(map));
            }
        });
        return new EnvironmentProfiles(byName);
    }

    public boolean isEmpty() {
        return byName.isEmpty();
    }

    /// The profile names in file order — what the status-bar override offers.
    public List<String> names() {
        return List.copyOf(byName.keySet());
    }

    /// What a switch to a task of a category applies: `override` over the
    /// category's `profile` over `default`, layered per key.
    /// A null name means "no such layer" — a category without `profile:` gets
    /// the default alone.
    ///
    /// @throws IllegalArgumentException naming a profile `settings.yaml` does
    ///     not define, so the chip says which name is wrong rather than
    ///     silently applying the default
    public EnvironmentProfile effective(@Nullable String categoryProfile, @Nullable String override) {
        EnvironmentProfile result = byName.getOrDefault(DEFAULT, EnvironmentProfile.EMPTY);
        for (String name : new String[] {categoryProfile, override}) {
            if (name == null || name.isBlank()) {
                continue;
            }
            EnvironmentProfile layer = byName.get(name.strip());
            if (layer == null) {
                throw new IllegalArgumentException("unknown profile \"%s\" (settings.yaml profiles: %s)"
                        .formatted(name.strip(), String.join(", ", byName.keySet())));
            }
            result = layer.over(result);
        }
        return result;
    }
}
