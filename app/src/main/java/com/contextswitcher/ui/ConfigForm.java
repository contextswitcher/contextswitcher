package com.contextswitcher.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import atlantafx.base.theme.Styles;

import com.contextswitcher.tasks.Frontmatter;
import com.contextswitcher.tasks.FrontmatterCatalog;
import com.contextswitcher.tasks.FrontmatterCatalog.Field;
import com.contextswitcher.tasks.TaskTags;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.jspecify.annotations.Nullable;

/// The configuration form's generation and write-back, for [ConfigFormPane]:
/// the frontmatter of the open task or category file as a generated key/value
/// form, so the fields are picked and filled instead of hand-written as YAML.
///
/// The form is generated from [FrontmatterCatalog] (one control per known key,
/// grouped by section), and the file text stays the source of truth: on apply
/// only the keys the user actually changed are patched back into it
/// ([Frontmatter#set]), so comments, key order, machine-suffixed variants and
/// keys the app does not know about survive an edit.
// [impl->dsn~task-field-form~4]
final class ConfigForm {

    private final List<Field> fields;
    /// One control per catalog field, keyed by the field's key.
    private final Map<String, Region> controls;

    /// The form for `fields`, its controls seeded from `content`'s frontmatter.
    ConfigForm(List<Field> fields, String content) {
        this.fields = List.copyOf(fields);
        this.controls = buildControls(this.fields, content);
    }

    /// One control per catalog field, seeded from the file's frontmatter and
    /// keyed by the field's key.
    private static Map<String, Region> buildControls(List<Field> fields, String content) {
        Map<String, Object> seeds = seeds(fields, content);
        Map<String, Region> controls = new LinkedHashMap<>();
        for (Field field : fields) {
            Object seed = seeds.get(field.key());
            Region control = switch (field.type()) {
                case STRING -> new TextField((String) seed);
                case BOOLEAN -> {
                    CheckBox box = new CheckBox();
                    box.setSelected((Boolean) seed);
                    yield box;
                }
                case ENUM -> {
                    ComboBox<String> combo =
                            new ComboBox<>(FXCollections.observableArrayList(field.choices()));
                    combo.getStyleClass().add(Styles.SMALL);
                    combo.setValue((String) seed);
                    yield combo;
                }
                case LIST -> FieldForm.listArea(
                        String.join("\n", asStringList(seed)), 3);
            };
            control.setId(field.controlId());
            controls.put(field.key(), control);
        }
        return controls;
    }

    /// Every field's start value, read out of `content`'s frontmatter: a
    /// [String] for a text field or drop-down, a [Boolean] for a checkbox, a
    /// list of lines for a list field. The pure half of [#buildControls] — and
    /// the counterpart of [#merge], which takes the same shapes back.
    static Map<String, Object> seeds(List<Field> fields, String content) {
        Map<String, Object> data = Frontmatter.parse(content);
        Map<String, Object> seeds = new LinkedHashMap<>();
        for (Field field : fields) {
            seeds.put(field.key(), switch (field.type()) {
                case STRING -> stringSeed(data, field.key());
                case BOOLEAN -> booleanSeed(data, field.key());
                case ENUM -> {
                    String value = stringSeed(data, field.key());
                    yield field.choices().contains(value) ? value : field.choices().getFirst();
                }
                case LIST -> listSeed(data, field.key());
            });
        }
        return seeds;
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object seed) {
        return (List<String>) seed;
    }

    /// The form: a header per catalog section, then one row per field.
    Node render() {
        VBox root = new VBox(2);
        root.setPadding(new Insets(6, 10, 6, 10));
        String group = null;
        for (Field field : fields) {
            if (!field.group().equals(group)) {
                group = field.group();
                root.getChildren().add(FieldForm.section(group, root.getChildren().isEmpty()));
            }
            root.getChildren().add(
                    FieldForm.row(field.label(), field.help(), controls.get(field.key())));
        }
        return root;
    }

    // --- seeding -----------------------------------------------------------

    private static String stringSeed(Map<String, Object> data, String key) {
        // `desktop:` comes as the plain name or as {name, completeControl}.
        Object value = key.equals("desktop") && data.get("desktop") instanceof Map<?, ?> nested
                ? nested.get("name")
                : Frontmatter.get(data, key);
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean booleanSeed(Map<String, Object> data, String key) {
        return switch (key) {
            // The bare `intellij:` key already enables the action — its mere
            // presence is the checkbox, the path below it is optional.
            case "intellij" -> data.containsKey("intellij");
            case "completeControl" -> data.get("desktop") instanceof Map<?, ?> nested
                    && Boolean.parseBoolean(String.valueOf(nested.get("completeControl")));
            default -> Boolean.parseBoolean(String.valueOf(Frontmatter.get(data, key)));
        };
    }

    private static List<String> listSeed(Map<String, Object> data, String key) {
        return switch (key) {
            case "tags" -> TaskTags.parse(data.get("tags"));
            case "folders" -> folders(data);
            case "browser.urls" -> urlLines(data);
            default -> stringList(Frontmatter.get(data, key));
        };
    }

    /// The local folders as the parser reads them: the `folders` list, or the
    /// single-directory `folder:` scalar a local category seeds.
    // [impl->dsn~explorer-folder-focus~3]
    private static List<String> folders(Map<String, Object> data) {
        Object folders = data.get("folders");
        return folders != null ? stringList(folders) : stringList(data.get("folder"));
    }

    /// `browser.urls` as editable lines: the address, and — for an entry that
    /// carries one — its title after the separator.
    // [impl->dsn~browser-url-title~1]
    private static List<String> urlLines(Map<String, Object> data) {
        if (!(Frontmatter.get(data, "browser.urls") instanceof List<?> urls)) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (Object entry : urls) {
            if (entry instanceof Map<?, ?> map) {
                Object url = map.get("url");
                Object title = map.get("title");
                lines.add(title == null
                        ? String.valueOf(url)
                        : url + FrontmatterCatalog.URL_TITLE_SEPARATOR + title);
            } else if (entry != null) {
                lines.add(String.valueOf(entry));
            }
        }
        return lines;
    }

    private static List<String> stringList(@Nullable Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(String::valueOf)
                    .filter(item -> !item.isBlank()).toList();
        }
        return value == null || String.valueOf(value).isBlank()
                ? List.of()
                : List.of(String.valueOf(value));
    }

    // --- writing back ------------------------------------------------------

    /// What applying the form gives: the patched file text, or why the form
    /// cannot be applied. Showing the reason is the caller's business.
    sealed interface MergeResult {

        record Merged(String content) implements MergeResult {
        }

        record Invalid(String reason) implements MergeResult {
        }
    }

    /// Reads the controls and patches the result back into `content` — see
    /// [#mergeValues].
    MergeResult mergeInto(String content) {
        Map<String, @Nullable Object> values = new LinkedHashMap<>();
        for (Field field : fields) {
            values.put(field.key(), read(field, controls.get(field.key())));
        }
        return mergeValues(content, fields, values);
    }

    /// [#merge], behind the form's one rule: a task keeps its title.
    static MergeResult mergeValues(String content, List<Field> fields,
            Map<String, @Nullable Object> values) {
        if (values.containsKey("title") && String.valueOf(values.get("title")).isBlank()) {
            return new MergeResult.Invalid("The title cannot be empty.");
        }
        return new MergeResult.Merged(merge(content, fields, values));
    }

    /// Patches every changed field back into `content`, leaving untouched keys,
    /// comments and unknown keys exactly as they were. `values` holds one entry
    /// per field — the shapes [#seeds] hands out, null for an unset field.
    static String merge(String content, List<Field> fields,
            Map<String, @Nullable Object> values) {
        Map<String, Object> data = Frontmatter.parse(content);
        // A field whose key is also some other field's parent is that section's
        // on/off switch (`intellij:`), not a value of its own.
        Set<String> sections = fields.stream().map(Field::parent).filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        Map<String, @Nullable Object> desired = new LinkedHashMap<>();
        Map<String, Map<String, Object>> children = new LinkedHashMap<>();
        Map<String, Boolean> enabled = new LinkedHashMap<>();
        for (Field field : fields) {
            Object value = normalize(field, values.get(field.key()));
            if (sections.contains(field.key())) {
                enabled.put(field.key(), value != null);
            } else if (field.parent() != null) {
                // Seeded from the current section, so children the form does
                // not show (claude.workspace, intellij.ide) survive.
                Map<String, Object> section = children.computeIfAbsent(field.parent(),
                        parent -> currentSection(data, parent));
                if (value == null) {
                    section.remove(field.child());
                } else {
                    section.put(field.child(), value);
                }
            } else {
                desired.put(field.key(), value);
            }
        }
        for (String parent : sections) {
            Map<String, Object> section =
                    children.computeIfAbsent(parent, key -> currentSection(data, key));
            // An enabled section with no children stays as the bare `{}` form
            // the parser reads as "configured, all defaults".
            desired.put(parent, enabled.getOrDefault(parent, !section.isEmpty()) ? section : null);
        }
        applyDesktopForm(desired);

        String out = content;
        boolean foldersChanged = false;
        for (Map.Entry<String, @Nullable Object> entry : desired.entrySet()) {
            if (Objects.equals(entry.getValue(), currentValue(data, entry.getKey()))) {
                continue;
            }
            out = Frontmatter.set(out, entry.getKey(), entry.getValue());
            foldersChanged |= entry.getKey().equals("folders");
        }
        // The `folders` list subsumes the single-directory `folder:` spelling a
        // local category seeds — writing the one must not leave the other
        // behind, or the file would name two sets of folders.
        if (foldersChanged && data.containsKey("folder")) {
            out = Frontmatter.set(out, "folder", null);
        }
        return out;
    }

    /// `desktop:` is written as the plain name, or as the nested
    /// `{name, completeControl}` form when the desktop is cleared on suspend —
    /// the two shapes the parser reads. The checkbox is not a key of its own.
    // [impl->dsn~complete-control-desktop~1]
    private static void applyDesktopForm(Map<String, @Nullable Object> desired) {
        if (!desired.containsKey("completeControl")) {
            return;
        }
        boolean completeControl = desired.remove("completeControl") != null;
        Object name = desired.get("desktop");
        if (name == null || !completeControl) {
            return;
        }
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("name", name);
        nested.put("completeControl", true);
        desired.put("desktop", nested);
    }

    /// The value of `key` as the file currently holds it, normalized the way
    /// the form writes it — so a `tags: a, b` string, a `folder:` scalar or a
    /// quoted `pinned: "true"` does not read as a change and get rewritten.
    private static @Nullable Object currentValue(Map<String, Object> data, String key) {
        return switch (key) {
            case "tags" -> nullIfEmpty(TaskTags.parse(data.get("tags")));
            case "folders" -> nullIfEmpty(folders(data));
            case "pinned", "autoDelete" -> Boolean.parseBoolean(String.valueOf(data.get(key)))
                    ? Boolean.TRUE
                    : null;
            default -> data.get(key);
        };
    }

    private static Map<String, Object> currentSection(Map<String, Object> data, String key) {
        Map<String, Object> section = new LinkedHashMap<>();
        if (data.get(key) instanceof Map<?, ?> current) {
            current.forEach((child, value) -> section.put(String.valueOf(child), value));
        }
        return section;
    }

    /// The control's value in the shape [#seeds] hands out.
    private static Object read(Field field, Region control) {
        return switch (field.type()) {
            case STRING -> ((TextField) control).getText();
            case BOOLEAN -> ((CheckBox) control).isSelected();
            case ENUM -> ((ComboBox<?>) control).getValue();
            case LIST -> ((TextArea) control).getText().lines().toList();
        };
    }

    /// A field's value as it is written, or null when the field is unset — a
    /// blank text, an unticked box, an empty list. The list fields drop blank
    /// lines, and the URL lines become `browser.urls` entries.
    private static @Nullable Object normalize(Field field, @Nullable Object value) {
        if (value == null) {
            return null;
        }
        return switch (field.type()) {
            case STRING, ENUM -> nullIfBlank(String.valueOf(value));
            case BOOLEAN -> Boolean.TRUE.equals(value) ? Boolean.TRUE : null;
            case LIST -> {
                List<String> lines = asStringList(value).stream()
                        .map(String::strip).filter(line -> !line.isEmpty()).toList();
                yield lines.isEmpty()
                        ? null
                        : field.key().equals("browser.urls") ? urlEntries(lines) : lines;
            }
        };
    }

    /// The URL lines as `browser.urls` entries: a plain scalar for the common
    /// untitled address, a `{url, title}` map for one carrying a title.
    // [impl->dsn~browser-url-title~1]
    private static List<Object> urlEntries(List<String> lines) {
        List<Object> entries = new ArrayList<>();
        for (String line : lines) {
            int separator = line.indexOf(FrontmatterCatalog.URL_TITLE_SEPARATOR);
            if (separator < 0) {
                entries.add(line);
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("url", line.substring(0, separator).strip());
            entry.put("title", line.substring(
                    separator + FrontmatterCatalog.URL_TITLE_SEPARATOR.length()).strip());
            entries.add(entry);
        }
        return entries;
    }

    private static @Nullable String nullIfBlank(String value) {
        return value.isBlank() ? null : value.strip();
    }

    private static @Nullable List<String> nullIfEmpty(List<String> value) {
        return value.isEmpty() ? null : value;
    }
}
