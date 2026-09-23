package com.contextswitcher.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import atlantafx.base.theme.Styles;
import com.contextswitcher.config.AppSettings;
import com.contextswitcher.config.Browser;
import com.contextswitcher.config.SettingsCatalog;
import com.contextswitcher.config.YamlPatch;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Window;
import org.jspecify.annotations.Nullable;
import tools.maran.svg.materialdesign.MDIInterface;
import tools.maran.svgnode.SvgNode;

/// The settings dialog behind the main toolbar's gear: the generated form over
/// `settings.yaml`, its raw view, and the raw-only fallback for a file too
/// malformed to parse. `MainWindow` hands in what it owns — loading and saving
/// the file, the dialog owner, the field-reference popup the editor shares,
/// and what a save refreshes live (the tag palette, the terminal's theme).
// [impl->dsn~settings-editor~4]
final class SettingsDialog {

    private final Supplier<String> loadSettings;
    /// Writes the new file content; null on success, else why not.
    private final Function<String, @Nullable String> saveSettings;
    /// The main window, the dialogs' owner; null before it is shown.
    private final Supplier<@Nullable Window> owner;
    /// Opens the non-modal field reference (title, reference text) — F1.
    private final BiConsumer<String, String> showReference;
    /// Re-reads the tag palette after a save.
    private final Runnable onPaletteChanged;
    /// Re-themes the terminal mirror after a save (its palette is not CSS).
    private final Runnable onRethemeTerminal;
    private final MainWindow.RefactoringMinerInstaller onSetupRefactoringMiner;

    SettingsDialog(Supplier<String> loadSettings, Function<String, @Nullable String> saveSettings,
            Supplier<@Nullable Window> owner, BiConsumer<String, String> showReference,
            Runnable onPaletteChanged, Runnable onRethemeTerminal,
            MainWindow.RefactoringMinerInstaller onSetupRefactoringMiner) {
        this.loadSettings = loadSettings;
        this.saveSettings = saveSettings;
        this.owner = owner;
        this.showReference = showReference;
        this.onPaletteChanged = onPaletteChanged;
        this.onRethemeTerminal = onRethemeTerminal;
        this.onSetupRefactoringMiner = onSetupRefactoringMiner;
    }

    /// Opens the settings dialog. The form is generated from [SettingsCatalog]
    /// (one control per known option, grouped by section) rather than hand-laid,
    /// so the option list is the single source of truth for what the dialog
    /// shows. Each row puts the label on its own line above a full-width control
    /// and highlights on hover. The raw `settings.yaml` text is the *data* source
    /// of truth: the form never regenerates the whole file — it patches only the
    /// keys the user actually changed back into the raw text ([YamlPatch]), so
    /// comments and any keys the app does not know about survive a form edit. A
    /// **View raw** toggle shows that same text; **F1** there opens the field
    /// reference. When the file on disk is too malformed to parse, the dialog
    /// opens straight in a raw-only fallback for repair.
    // [impl->dsn~settings-editor~4]
    void show() {
        String raw = loadSettings.get();
        AppSettings initial;
        try {
            initial = AppSettings.parse(raw);
        } catch (IOException e) {
            editSettingsRaw(raw);
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner.get());
        dialog.setTitle("Settings");
        // The app mark next to the header text — the one place the icon is
        // visible without hunting for a taskbar button. [impl->dsn~app-icon~4]
        dialog.setGraphic(new ImageView(AppIcon.image(48)));
        dialog.setHeaderText("settings.yaml is the source of truth — the form patches only the "
                + "fields you change, keeping comments and unknown keys. Restart to apply "
                + "(the tag palette, theme, browser and the auto-suspend threshold update live).");
        dialog.setResizable(true);
        ButtonType saveButton = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType rawToggle = new ButtonType("View raw", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(rawToggle, saveButton, ButtonType.CANCEL);

        // The canonical text buffer: starts as the file text, gets the changed
        // keys patched into it, and is what Save writes — whichever view is up.
        String[] rawText = {raw};
        TextArea rawArea = new TextArea();
        rawArea.getStyleClass().add("monospace");
        rawArea.setPrefColumnCount(64);
        rawArea.setPrefRowCount(20);
        rawArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F1) {
                showReference.accept("Settings fields — settings.yaml",
                        AppSettings.settingsReference());
                event.consume();
            }
        });

        // The controls are rebuilt (and the rendered form replaced in the scroll
        // pane) whenever we re-enter form view, so they reflect the latest text.
        // A one-element holder lets the toggle/save handlers see the current set.
        @SuppressWarnings("unchecked")
        Map<String, Region>[] controlsHolder = new Map[]{buildControls(initial)};
        ScrollPane formScroll = new ScrollPane(renderForm(controlsHolder[0]));
        formScroll.setFitToWidth(true);
        formScroll.setPrefViewportHeight(FieldForm.viewportHeight());
        formScroll.setPrefViewportWidth(460);

        boolean[] rawMode = {false};
        dialog.getDialogPane().setContent(formScroll);
        Button rawButton = (Button) dialog.getDialogPane().lookupButton(rawToggle);
        rawButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (rawMode[0]) {
                // Raw → form: the edited raw text becomes canonical, then seeds a
                // freshly built form. Malformed text keeps raw view.
                AppSettings parsed;
                try {
                    parsed = AppSettings.parse(rawArea.getText());
                } catch (IOException e) {
                    new Alert(Alert.AlertType.ERROR,
                            "Cannot switch to form view: " + e.getMessage()).show();
                    return;
                }
                rawText[0] = rawArea.getText();
                controlsHolder[0] = buildControls(parsed);
                formScroll.setContent(renderForm(controlsHolder[0]));
                dialog.getDialogPane().setContent(formScroll);
                rawButton.setText("View raw");
                rawMode[0] = false;
            } else {
                // Form → raw: patch the changed keys into the canonical text and
                // show it. A blank tasks dir / bad port blocks the switch.
                String merged = mergeFormIntoRaw(rawText[0], controlsHolder[0]);
                if (merged == null) {
                    return;
                }
                rawText[0] = merged;
                rawArea.setText(merged);
                dialog.getDialogPane().setContent(rawArea);
                rawButton.setText("Form view");
                rawMode[0] = true;
            }
        });

        // Save writes the canonical text: the raw buffer as edited, or — in form
        // view — that buffer with the changed keys patched in. An error keeps the
        // dialog open rather than dismissing on a failed write.
        Button saveNode = (Button) dialog.getDialogPane().lookupButton(saveButton);
        saveNode.addEventFilter(ActionEvent.ACTION, event -> {
            String content;
            if (rawMode[0]) {
                content = rawArea.getText();
            } else {
                String merged = mergeFormIntoRaw(rawText[0], controlsHolder[0]);
                if (merged == null) {
                    event.consume();
                    return;
                }
                content = merged;
            }
            String error = saveSettings.apply(content);
            if (error != null) {
                new Alert(Alert.AlertType.ERROR, "Cannot save settings: " + error).show();
                event.consume();
                return;
            }
            // The tag palette is the one setting with live UI presence — re-read
            // it so the Tags menu and chips update without a restart.
            onPaletteChanged.run();
            // Apply the theme live too, so a light/dark switch takes effect at
            // once (the user-agent stylesheet swap restyles every open window,
            // icons included — their fill is CSS); only the terminal mirror
            // needs its own (its palette is not CSS at all). [impl->dsn~theme-select~8]
            try {
                Themes.apply(AppSettings.parse(content).theme());
                // [impl->dsn~terminal-theme~2]
                onRethemeTerminal.run();
            } catch (IOException ignored) {
                // Raw text the user just saved was unparseable — leave the theme
                // as it is; the save itself already succeeded.
            }
        });
        keepTitleBarOnScreen(dialog);
        dialog.showAndWait();
    }

    /// One control per [SettingsCatalog] option, seeded from `seed` and keyed by
    /// its settings key. Text and integer options render as a `TextField`, the
    /// booleans as a `CheckBox`, and the list options (remotes, tag palette) as a
    /// multi-line `TextArea`, one entry per line.
    // [impl->dsn~settings-editor~4]
    private Map<String, Region> buildControls(AppSettings seed) {
        Map<String, Region> controls = new LinkedHashMap<>();
        for (SettingsCatalog.Option option : SettingsCatalog.OPTIONS) {
            String key = option.key();
            Region control = switch (option.type()) {
                case STRING -> new TextField((String) seed.yamlValue(key));
                case INTEGER -> new TextField(String.valueOf(seed.yamlValue(key)));
                case BOOLEAN -> {
                    CheckBox box = new CheckBox();
                    box.setSelected((Boolean) seed.yamlValue(key));
                    yield box;
                }
                case STRING_LIST -> FieldForm.listArea(String.join("\n", seed.remotes()), 3);
                case TAG_LIST -> new TagListEditor(seed.tags());
                case ENUM -> {
                    ComboBox<String> combo = new ComboBox<>(
                            FXCollections.observableArrayList(option.choices()));
                    combo.getStyleClass().add(Styles.SMALL);
                    combo.setValue((String) seed.yamlValue(key));
                    yield combo;
                }
            };
            controls.put(key, control);
        }
        return controls;
    }

    /// Lays the controls out as the settings form: a bold section header per
    /// [SettingsCatalog.Option] group, then one row per option with the label on
    /// its own line above a full-width control, the whole row highlighting on
    /// hover and carrying the option's help as its tooltip.
    // [impl->dsn~settings-editor~4]
    private Node renderForm(Map<String, Region> controls) {
        VBox root = new VBox(2);
        root.setPadding(new Insets(6, 10, 6, 10));
        String group = null;
        for (SettingsCatalog.Option option : SettingsCatalog.OPTIONS) {
            if (!option.group().equals(group)) {
                group = option.group();
                root.getChildren().add(FieldForm.section(group, root.getChildren().isEmpty()));
            }
            root.getChildren().add(fieldRow(option, controls));
        }
        return root;
    }

    /// One settings form row — the shared [FieldForm] row, plus the one action
    /// beside a field: the RefactoringMiner path is otherwise a manual unzip on
    /// every remote. [impl->dsn~refactoring-miner-setup~1]
    // [impl->dsn~settings-editor~4]
    private Node fieldRow(SettingsCatalog.Option option, Map<String, Region> controls) {
        Region control = controls.get(option.key());
        return option.key().equals("refactoringMinerHome")
                ? FieldForm.row(option.label(), option.help(), control,
                        setupRefactoringMinerButton(controls))
                : FieldForm.row(option.label(), option.help(), control);
    }

    /// The **Set up** button beside the RefactoringMiner directory: installs the
    /// release on every remote the form lists and fills the field with the
    /// directory it landed in — one click instead of a manual unzip per remote.
    /// Disabled for the round-trip, status bar carrying the outcome (the async
    /// single-shot convention); the value still needs a **Save**.
    // [impl->dsn~refactoring-miner-setup~1]
    private Button setupRefactoringMinerButton(Map<String, Region> controls) {
        Button setup = new Button("Set up");
        setup.getStyleClass().add(Styles.SMALL);
        setup.setTooltip(new Tooltip("Download and unzip RefactoringMiner on the configured "
                + "remotes, then fill in the directory."));
        setup.setOnAction(event -> {
            List<String> remotes = ((TextArea) controls.get("remotes")).getText().lines()
                    .map(String::trim).filter(line -> !line.isEmpty()).toList();
            if (remotes.isEmpty()) {
                new Alert(Alert.AlertType.INFORMATION,
                        "RefactoringMiner runs on the remotes — configure a remote first.").show();
                return;
            }
            TextField home = (TextField) controls.get("refactoringMinerHome");
            setup.setDisable(true);
            onSetupRefactoringMiner.install(remotes, installed -> {
                setup.setDisable(false);
                if (installed != null) {
                    home.setText(installed);
                }
            });
        });
        return setup;
    }

    /// Patches every changed catalog key from the form back into `raw`, leaving
    /// untouched keys, comments, and unknown keys exactly as they were — the raw
    /// text stays the source of truth. Returns null (after an alert) when the
    /// form is invalid (blank tasks dir, non-numeric port).
    // [impl->dsn~settings-editor~4]
    private @Nullable String mergeFormIntoRaw(String raw, Map<String, Region> controls) {
        AppSettings edited = readForm(controls);
        if (edited == null) {
            return null;
        }
        AppSettings current;
        try {
            current = AppSettings.parse(raw);
        } catch (IOException e) {
            // The buffer was valid when it entered form view; if it somehow is
            // not, patch every key rather than silently dropping edits.
            current = null;
        }
        String out = raw;
        for (SettingsCatalog.Option option : SettingsCatalog.OPTIONS) {
            Object newValue = edited.yamlValue(option.key());
            Object oldValue = current == null ? null : current.yamlValue(option.key());
            if (!newValue.equals(oldValue)) {
                out = YamlPatch.set(out, option.key(), newValue);
            }
        }
        return out;
    }

    /// Reads the form controls into an [AppSettings], or null (after an alert)
    /// when a field is invalid — a blank tasks directory or a non-numeric port.
    /// The list fields split one entry per line.
    // [impl->dsn~settings-editor~4]
    private @Nullable AppSettings readForm(Map<String, Region> controls) {
        String tasksDir = ((TextField) controls.get("tasksDir")).getText();
        if (tasksDir.isBlank()) {
            new Alert(Alert.AlertType.ERROR, "The tasks directory cannot be empty.").show();
            return null;
        }
        int wsPort;
        try {
            wsPort = Integer.parseInt(((TextField) controls.get("wsPort")).getText().trim());
        } catch (NumberFormatException e) {
            new Alert(Alert.AlertType.ERROR, "The WebSocket port must be a whole number.").show();
            return null;
        }
        String wsToken = ((TextField) controls.get("wsToken")).getText();
        boolean hints = ((CheckBox) controls.get("hints")).isSelected();
        boolean claudeAuto = ((CheckBox) controls.get("claudeAuto")).isSelected();
        List<String> remotes = ((TextArea) controls.get("remotes")).getText().lines()
                .map(String::trim).filter(line -> !line.isEmpty()).toList();
        List<AppSettings.TagDef> tags = ((TagListEditor) controls.get("tags")).getTags();
        @SuppressWarnings("unchecked")
        String theme = ((ComboBox<String>) controls.get("theme")).getValue();
        boolean showOnAllDesktops = ((CheckBox) controls.get("showOnAllDesktops")).isSelected();
        // [impl->dsn~readline-keys~1]
        boolean readlineKeys = ((CheckBox) controls.get("readlineKeys")).isSelected();
        // [impl->dsn~refactoring-web-view~1]
        String refactoringMinerHome =
                ((TextField) controls.get("refactoringMinerHome")).getText().trim();
        int refactoringMinerPort;
        try {
            refactoringMinerPort = Integer.parseInt(
                    ((TextField) controls.get("refactoringMinerPort")).getText().trim());
        } catch (NumberFormatException e) {
            new Alert(Alert.AlertType.ERROR,
                    "The refactoring view port must be a whole number.").show();
            return null;
        }
        // These three are on the form but used to be dropped on the way back,
        // so saving any field reset them to their defaults.
        // [impl->dsn~fallback-desktop~1]
        String fallbackDesktop = ((TextField) controls.get("fallbackDesktop")).getText();
        int autoSuspendMinutes;
        try {
            autoSuspendMinutes = Integer.parseInt(
                    ((TextField) controls.get("autoSuspendMinutes")).getText().trim());
        } catch (NumberFormatException e) {
            new Alert(Alert.AlertType.ERROR,
                    "The auto-suspend minutes must be a whole number.").show();
            return null;
        }
        @SuppressWarnings("unchecked")
        Browser browser = Browser.of(((ComboBox<String>) controls.get("browser")).getValue());
        return new AppSettings(Path.of(tasksDir.trim()), wsPort, wsToken,
                remotes, tags, hints, claudeAuto, theme, showOnAllDesktops,
                refactoringMinerHome, refactoringMinerPort, readlineKeys,
                fallbackDesktop, autoSuspendMinutes, browser);
    }

    /// The tag-palette editor: a row per tag pairing a name field with a color
    /// picker (a tag is always a name + color), plus an "Add tag" button and a
    /// per-row remove. This is the one setting whose value is not a flat scalar
    /// or line list, so it gets a purpose-built control instead of a text box.
    // [impl->dsn~settings-editor~4]
    // [impl->dsn~task-tag-model~2]
    private static final class TagListEditor extends VBox {

        /// A muted default for a tag that had no color yet, so a color picker is
        /// never seeded with an arbitrary bright value.
        private static final Color DEFAULT_TAG_COLOR = Color.web("#8b949e");

        private final VBox rows = new VBox(4);

        TagListEditor(List<AppSettings.TagDef> tags) {
            super(6);
            for (AppSettings.TagDef tag : tags) {
                rows.getChildren().add(tagRow(tag.name(), tag.color()));
            }
            Button add = new Button("Add tag", new SvgNode(MDIInterface.PLUS.path(), 14));
            add.getStyleClass().add(Styles.SMALL);
            add.setOnAction(event -> rows.getChildren().add(tagRow("", null)));
            getChildren().addAll(rows, add);
        }

        private HBox tagRow(String name, @Nullable String color) {
            TextField nameField = new TextField(name);
            nameField.setPromptText("tag name");
            HBox.setHgrow(nameField, Priority.ALWAYS);
            ColorPicker picker = new ColorPicker(colorOf(color));
            picker.getStyleClass().add(Styles.SMALL);
            Button remove = MainWindow.iconButton(MDIInterface.TRASH_CAN_OUTLINE, "Remove tag");
            HBox row = new HBox(6, nameField, picker, remove);
            row.setAlignment(Pos.CENTER_LEFT);
            remove.setOnAction(event -> rows.getChildren().remove(row));
            return row;
        }

        private static Color colorOf(@Nullable String hex) {
            if (hex == null) {
                return DEFAULT_TAG_COLOR;
            }
            try {
                return Color.web(hex);
            } catch (IllegalArgumentException e) {
                return DEFAULT_TAG_COLOR;
            }
        }

        /// The edited palette: every row with a non-blank name, its picked color
        /// as a `#rrggbb` hex. Empty-name rows are dropped (an unfilled "Add tag").
        List<AppSettings.TagDef> getTags() {
            List<AppSettings.TagDef> tags = new ArrayList<>();
            for (Node node : rows.getChildren()) {
                HBox row = (HBox) node;
                String name = ((TextField) row.getChildren().get(0)).getText().strip();
                if (name.isEmpty()) {
                    continue;
                }
                Color color = ((ColorPicker) row.getChildren().get(1)).getValue();
                tags.add(new AppSettings.TagDef(name, toHex(color)));
            }
            return tags;
        }

        private static String toHex(Color color) {
            return String.format("#%02x%02x%02x",
                    Math.round(color.getRed() * 255),
                    Math.round(color.getGreen() * 255),
                    Math.round(color.getBlue() * 255));
        }
    }

    /// The raw-only fallback editor: a monospace `settings.yaml` `TextArea`
    /// (F1 = field reference), used when the file on disk is too malformed to
    /// populate the form. Save writes the text straight back.
    // [impl->dsn~settings-editor~4]
    private void editSettingsRaw(String raw) {
        Dialog<String> dialog = new Dialog<>();
        dialog.initOwner(owner.get());
        dialog.setTitle("Edit settings");
        dialog.setHeaderText("settings.yaml could not be parsed — edit the raw text. "
                + "Restart to apply. F1: field reference.");
        dialog.setResizable(true);
        ButtonType saveButton = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);

        TextArea area = new TextArea(raw);
        area.getStyleClass().add("monospace");
        area.setPrefColumnCount(64);
        area.setPrefRowCount(18);
        area.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F1) {
                showReference.accept("Settings fields — settings.yaml",
                        AppSettings.settingsReference());
                event.consume();
            }
        });
        dialog.getDialogPane().setContent(area);
        dialog.setResultConverter(button -> button == saveButton ? area.getText() : null);
        Platform.runLater(() -> {
            area.requestFocus();
            area.end();
        });
        keepTitleBarOnScreen(dialog);
        dialog.showAndWait().ifPresent(content -> {
            String error = saveSettings.apply(content);
            if (error != null) {
                new Alert(Alert.AlertType.ERROR, "Cannot save settings: " + error).show();
                return;
            }
            onPaletteChanged.run();
        });
    }

    /// JavaFX centers a dialog on its owner, and this one is taller than the
    /// main window — its height follows the screen — so it can start above the
    /// screen with its title bar out of reach. Checked once shown: the height
    /// is not known before.
    private static void keepTitleBarOnScreen(Dialog<?> dialog) {
        dialog.setOnShown(event -> {
            double top = Screen.getScreensForRectangle(dialog.getX(), dialog.getY(),
                    dialog.getWidth(), dialog.getHeight()).stream().findFirst()
                    .orElse(Screen.getPrimary()).getVisualBounds().getMinY();
            if (dialog.getY() < top) {
                dialog.setY(top);
            }
        });
    }
}
