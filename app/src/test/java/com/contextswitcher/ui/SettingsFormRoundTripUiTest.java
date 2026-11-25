package com.contextswitcher.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import com.contextswitcher.config.AppSettings;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Saving the settings form without touching a field leaves `settings.yaml`
/// exactly as it was. `readForm` used to build its result through a
/// back-compat constructor that filled `mergedCleanupDays` and
/// `autoCopyReplies` with their defaults, so every save reset both. The file
/// here gives **every** catalog key a non-default value, so a key the form
/// drops on the way back — today's or a future one — shows up as a patch.
// [utest->dsn~settings-editor~5]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class SettingsFormRoundTripUiTest {

    private static final String RAW = """
            tasksDir: /home/o/tasks
            wsPort: 17999
            wsToken: secret
            hints: false
            claudeAuto: true
            theme: dark
            showOnAllDesktops: true
            fallbackDesktop: other
            readlineKeys: true
            autoSuspendMinutes: 30
            mergedCleanupDays: 3
            autoCopyReplies: true
            browser: chrome
            refactoringMinerPort: 7000
            hosts:
            - destination: koppor@devbox
              active: true
              workspaces: /home/koppor/workspaces
              refactoringMiner: /opt/rm
            - destination: oldbox
              active: false
            tags:
            - name: jabref
              color: '#2da44e'
            """;

    @Test
    void unchangedFormPatchesNothing() throws Exception {
        SettingsDialog dialog = new SettingsDialog(() -> RAW, content -> null, () -> null,
                (title, text) -> { }, () -> { }, () -> { }, (remotes, onDone) -> { });
        CompletableFuture<String> merged = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                merged.complete(dialog.mergeFormIntoRaw(RAW,
                        dialog.buildControls(AppSettings.parse(RAW))));
            } catch (Throwable e) {
                merged.completeExceptionally(e);
            }
        });
        assertThat(merged.get(10, TimeUnit.SECONDS)).isEqualTo(RAW);
    }

    @Test
    void formHasATabPerGroupAndKeepsTheSelectedOneOnRebuild() throws Exception {
        SettingsDialog dialog = new SettingsDialog(() -> RAW, content -> null, () -> null,
                (title, text) -> { }, () -> { }, () -> { }, (remotes, onDone) -> { });
        CompletableFuture<List<String>> titles = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                TabPane tabs = new TabPane();
                dialog.renderForm(tabs, dialog.buildControls(AppSettings.parse(RAW)));
                tabs.getSelectionModel().select(2);
                dialog.renderForm(tabs, dialog.buildControls(AppSettings.parse(RAW)));
                List<String> result = new ArrayList<>(tabs.getTabs().stream().map(Tab::getText).toList());
                result.add("selected " + tabs.getSelectionModel().getSelectedIndex());
                titles.complete(result);
            } catch (Throwable e) {
                titles.completeExceptionally(e);
            }
        });
        assertThat(titles.get(10, TimeUnit.SECONDS)).containsExactly("General", "Claude", "Interface",
                "Browser", "Hosts", "Tags", "selected 2");
    }

    @Test
    void hostsTabShowsItsTableWithoutCutting() throws Exception {
        SettingsDialog dialog = new SettingsDialog(() -> RAW, content -> null, () -> null,
                (title, text) -> { }, () -> { }, () -> { }, (remotes, onDone) -> { });
        CompletableFuture<TabPane> shown = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                TabPane tabs = new TabPane();
                dialog.renderForm(tabs, dialog.buildControls(AppSettings.parse(RAW)));
                tabs.getSelectionModel().select(4);
                Stage stage = new Stage();
                stage.setScene(new Scene(tabs));
                stage.show();
                shown.complete(tabs);
            } catch (Throwable e) {
                shown.completeExceptionally(e);
            }
        });
        TabPane tabs = shown.get(10, TimeUnit.SECONDS);
        assertThat(UiTestSupport.truncatedTexts(tabs)).isEmpty();
        Platform.runLater(() -> tabs.getScene().getWindow().hide());
    }
}
