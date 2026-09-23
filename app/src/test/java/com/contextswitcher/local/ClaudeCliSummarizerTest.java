package com.contextswitcher.local;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-create-local-title~1]
class ClaudeCliSummarizerTest {

    private static final Path TEMP = Path.of("tmp");

    @Test
    void onWindowsTheShimRunsThroughCmd() {
        assertThat(ClaudeCliSummarizer.command(true)).startsWith("cmd", "/c", "claude", "-p");
    }

    @Test
    void elsewhereClaudeRunsByItsResolvedPath() {
        List<String> command = ClaudeCliSummarizer.command(false);
        assertThat(command.getFirst()).endsWith("claude").doesNotStartWith("cmd");
        assertThat(command.get(1)).isEqualTo("-p");
    }

    @Test
    void theModelGetsNoToolsNoSettingsAndOnlyTheInstruction() {
        List<String> command = ClaudeCliSummarizer.command(true);
        assertThat(command)
                .containsSequence("--model", "haiku")
                .containsSequence("--tools", "")
                .containsSequence("--setting-sources", "")
                .contains("--no-session-persistence")
                .containsSequence("--output-format", "text");
        assertThat(command.getLast()).isEqualTo(ClaudeCliSummarizer.INSTRUCTION);
    }

    @Test
    void theDescriptionTravelsOnStdinNotAsAnArgument() {
        String description = " Build \"%PATH%\" <nightly> & tests\nfail on Windows ";
        Recording runner = new Recording(new LocalCommandRunner.LocalResult(0, "Fix nightly build", ""));

        new ClaudeCliSummarizer(runner, true, TEMP).summarize(description);

        assertThat(runner.input).isEqualTo(description.strip());
        assertThat(runner.command).noneMatch(argument -> argument.contains("nightly"));
        assertThat(runner.directory).isEqualTo(TEMP);
    }

    @Test
    void theAnswerIsSanitized() {
        Recording runner = new Recording(new LocalCommandRunner.LocalResult(0, "\"Fix Safari login.\"\n", ""));

        assertThat(new ClaudeCliSummarizer(runner, false, TEMP).summarize("text")).contains("Fix Safari login");
    }

    @Test
    void aFailedOrTimedOutCallGivesNoTitle() {
        Recording failed = new Recording(new LocalCommandRunner.LocalResult(1, "partial", "not logged in"));
        Recording timedOut = new Recording(new LocalCommandRunner.LocalResult(-1, "", "Timeout after PT45S"));

        assertThat(new ClaudeCliSummarizer(failed, false, TEMP).summarize("text")).isEmpty();
        assertThat(new ClaudeCliSummarizer(timedOut, false, TEMP).summarize("text")).isEmpty();
    }

    /// Records the one call and answers with a canned result; starts nothing.
    private static final class Recording extends LocalCommandRunner {
        private final LocalResult result;
        private List<String> command = List.of();
        private @Nullable Path directory;
        private String input = "";

        private Recording(LocalResult result) {
            this.result = result;
        }

        @Override
        public LocalResult run(List<String> command, @Nullable Path directory, byte @Nullable [] input) {
            this.command = command;
            this.directory = directory;
            this.input = input == null ? "" : new String(input, StandardCharsets.UTF_8);
            return result;
        }
    }
}
