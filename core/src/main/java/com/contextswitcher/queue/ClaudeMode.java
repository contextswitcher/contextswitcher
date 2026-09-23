package com.contextswitcher.queue;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/// The model and reasoning effort a message should be answered with —
/// chosen next to the send button (queue) or in the Add-task dialog, and
/// delivered as Claude's own `/model` and `/effort` slash commands sent
/// **ahead** of the message itself. Both are null in [#DEFAULT]: leave the
/// session as it is, and nothing extra is sent.
///
/// The commands change the session, not one turn: Claude keeps the picked
/// model and effort for every later message too (there is no per-message
/// override). Hence the picker resets after a send — the session now *is*
/// what was picked.
// [impl->dsn~claude-mode-select~3]
public record ClaudeMode(@Nullable String model, @Nullable String effort) {

    /// Leave the session's model and effort untouched.
    public static final ClaudeMode DEFAULT = new ClaudeMode(null, null);

    /// `/model` arguments offered in the pickers — the aliases, not the
    /// full model ids: an alias keeps working across model releases.
    public static final List<String> MODELS =
            List.of("default", "opus", "opus[1m]", "sonnet", "sonnet[1m]", "haiku", "fable",
                    "opusplan", "best");

    /// `/effort` arguments offered in the pickers, cheapest first.
    public static final List<String> EFFORTS = List.of("low", "medium", "high", "xhigh", "max");

    /// The slash commands to submit before the message, in order — empty
    /// for [#DEFAULT]. Each is delivered as its own message (own paste, own
    /// Enter): Claude runs one command per submission.
    public List<String> commands() {
        List<String> commands = new ArrayList<>(2);
        if (model != null && !model.isBlank()) {
            commands.add("/model " + model.strip());
        }
        if (effort != null && !effort.isBlank()) {
            commands.add("/effort " + effort.strip());
        }
        return List.copyOf(commands);
    }
}
