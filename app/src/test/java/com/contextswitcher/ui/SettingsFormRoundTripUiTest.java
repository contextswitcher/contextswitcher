package com.contextswitcher.ui;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;

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
// [utest->dsn~settings-editor~4]
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
            refactoringMinerHome: /opt/rm
            refactoringMinerPort: 7000
            remotes:
            - devbox
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
}
