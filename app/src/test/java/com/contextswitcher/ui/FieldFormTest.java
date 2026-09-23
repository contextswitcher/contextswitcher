package com.contextswitcher.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The scrolling forms' height rule, without a screen: the whole dialog stays
/// within three quarters of the screen height.
// [utest->dsn~settings-editor~4]
class FieldFormTest {

    @Test
    void theWholeDialogStaysWithinThreeQuartersOfTheScreen() {
        assertThat(FieldForm.viewportHeightOn(1040) + FieldForm.DIALOG_CHROME).isEqualTo(780);
    }

    @Test
    void aTinyScreenStillShowsSomeOfTheForm() {
        assertThat(FieldForm.viewportHeightOn(300)).isEqualTo(FieldForm.MIN_VIEWPORT);
    }
}
