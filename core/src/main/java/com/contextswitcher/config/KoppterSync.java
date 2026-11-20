package com.contextswitcher.config;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/// The projects and contexts ContextSwitcher shares with koppter's todo.txt
/// filter (`koppterSync: true` in `settings.yaml`).
///
/// A category names its todo.txt project with `project:` in its
/// `CONTEXTSWITCHER.md` (several categories may share one — the same project
/// worked on this machine and on a remote) and, optionally, the `contexts:`
/// it belongs to. The current *context* is the environment profile the
/// screens or the status-bar override chose ([#profileContext]).
///
/// Outgoing: `koppter://sync?project=…&context=…` ([#koppterUrl]); incoming:
/// `contextswitcher://filter?project=…&context=…` ([#parseQuery]). A key a
/// link does not name is cleared. [Guard] keeps a selection from going back
/// to where it came from.
// [impl->dsn~koppter-sync~1]
public final class KoppterSync {

    /// The `settings.yaml` key that turns the sharing on.
    public static final String SETTING = "koppterSync";

    /// The separator of nested todo.txt projects and contexts: `papers>survey`
    /// lies below `papers`.
    private static final String LEVEL = ">";

    private KoppterSync() {
    }

    /// A project and context selection; empty sets restrict nothing.
    public record Selection(Set<String> projects, Set<String> contexts) {

        public static final Selection EMPTY = new Selection(Set.of(), Set.of());

        public Selection {
            projects = java.util.Collections.unmodifiableSet(new TreeSet<>(projects));
            contexts = java.util.Collections.unmodifiableSet(new TreeSet<>(contexts));
        }

        public boolean isEmpty() {
            return projects.isEmpty() && contexts.isEmpty();
        }

        public Selection withProjects(Set<String> newProjects) {
            return new Selection(newProjects, contexts);
        }

        public Selection withContexts(Set<String> newContexts) {
            return new Selection(projects, newContexts);
        }

        /// Whether a category with this `project:` and these `contexts:`
        /// belongs to the selection: a selected project matches itself and
        /// the projects below it, and a category without `contexts:` belongs
        /// to every context (it is not tied to a situation). A category
        /// without `project:` falls out while a project is selected.
        public boolean matches(@Nullable String project, Collection<String> categoryContexts) {
            boolean projectOk = projects.isEmpty()
                    || (project != null && projects.stream().anyMatch(p -> within(project, p)));
            boolean contextOk = contexts.isEmpty() || categoryContexts.isEmpty()
                    || categoryContexts.stream().anyMatch(c -> contexts.stream().anyMatch(s -> within(c, s)));
            return projectOk && contextOk;
        }

        @Override
        public String toString() {
            List<String> parts = new ArrayList<>();
            projects.forEach(p -> parts.add("+" + p));
            contexts.forEach(c -> parts.add("@" + c));
            return parts.isEmpty() ? "no project or context" : String.join(" ", parts);
        }
    }

    /// Whether `name` is `ancestor` or lies below it (`a>b` within `a`), case-insensitive.
    static boolean within(String name, String ancestor) {
        return name.equalsIgnoreCase(ancestor)
                || name.toLowerCase(java.util.Locale.ROOT).startsWith(ancestor.toLowerCase(java.util.Locale.ROOT) + LEVEL);
    }

    /// Whether `settings.yaml` turns the sharing on.
    public static boolean enabled(String settingsYaml) {
        return root(settingsYaml).get(SETTING) instanceof Boolean on && on;
    }

    /// The todo.txt context of the environment profile `profile`: its
    /// `context:` key, else its name; none for no profile, for `default`
    /// without `context:`, and for `context: off` (or `false`/blank).
    public static @Nullable String profileContext(String settingsYaml, @Nullable String profile) {
        if (profile == null || profile.isBlank()) {
            return null;
        }
        String name = profile.strip();
        Object context = root(settingsYaml).get("profiles") instanceof Map<?, ?> profiles
                && profiles.get(name) instanceof Map<?, ?> entry
                && entry.containsKey("context") ? entry.get("context") : name;
        if (context == null || Boolean.FALSE.equals(context)
                || context.toString().isBlank() || context.toString().equalsIgnoreCase("off")) {
            return null;
        }
        if (context.equals(name) && name.equals(EnvironmentProfiles.DEFAULT)) {
            return null;
        }
        return context.toString().strip().replaceFirst("^@", "");
    }

    /// The link that hands `selection` to koppter.
    public static String koppterUrl(Selection selection) {
        return "koppter://sync" + query(selection);
    }

    /// The `contextswitcher://filter` link of `selection` — what koppter sends.
    public static String filterUrl(Selection selection) {
        return "contextswitcher://filter" + query(selection);
    }

    private static String query(Selection selection) {
        List<String> parts = new ArrayList<>();
        selection.projects().forEach(p -> parts.add("project=" + encode(p)));
        selection.contexts().forEach(c -> parts.add("context=" + encode(c)));
        return parts.isEmpty() ? "" : "?" + String.join("&", parts);
    }

    /// Encodes all but letters, digits and `-._~`, as koppter's own links do,
    /// so a program that finds links in a text takes the whole link.
    private static String encode(String value) {
        StringBuilder out = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xFF);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || "-._~".indexOf(c) >= 0) {
                out.append(c);
            } else {
                out.append('%').append(String.format("%02X", b & 0xFF));
            }
        }
        return out.toString();
    }

    /// The selection of a raw (still percent-encoded) query such as
    /// `project=papers%3Esurvey&context=desk`; null or blank is the empty
    /// selection. Names may carry their todo.txt sign (`+`, `@`); unknown keys
    /// are ignored.
    public static Selection parseQuery(@Nullable String rawQuery) {
        Set<String> projects = new LinkedHashSet<>();
        Set<String> contexts = new LinkedHashSet<>();
        if (rawQuery != null) {
            for (String part : rawQuery.split("&")) {
                int eq = part.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String key = part.substring(0, eq).toLowerCase(java.util.Locale.ROOT);
                String value = decode(part.substring(eq + 1));
                if (key.equals("project")) {
                    add(projects, value.replaceFirst("^\\+", ""));
                } else if (key.equals("context")) {
                    add(contexts, value.replaceFirst("^@", ""));
                }
            }
        }
        return new Selection(projects, contexts);
    }

    private static void add(Set<String> into, String value) {
        if (!value.isBlank()) {
            into.add(value.strip());
        }
    }

    /// Percent-decoding that leaves a literal `+` alone (a project's sign, not
    /// a space as in form encoding).
    private static String decode(String value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%' && i + 2 < value.length()) {
                try {
                    bytes.write(Integer.parseInt(value.substring(i + 1, i + 3), 16));
                    i += 2;
                    continue;
                } catch (NumberFormatException e) {
                    // a lone '%' stays as it is
                }
            }
            byte[] chunk = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
            bytes.write(chunk, 0, chunk.length);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static Map<?, ?> root(String settingsYaml) {
        try {
            return new Yaml(new SafeConstructor(new LoaderOptions())).load(settingsYaml) instanceof Map<?, ?> map
                    ? map : Map.of();
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    /// Keeps ContextSwitcher and koppter from sending each other the same
    /// selection back and forth: a selection is sent only when it differs from
    /// the last one sent or received.
    public static final class Guard {

        private @Nullable Selection last;

        /// Whether `selection` is to be sent; it is then the last one.
        public synchronized boolean send(Selection selection) {
            boolean differs = !selection.equals(last);
            last = selection;
            return differs;
        }

        /// Takes a selection that arrived from koppter, so it is not sent back.
        public synchronized void received(Selection selection) {
            last = selection;
        }
    }
}
