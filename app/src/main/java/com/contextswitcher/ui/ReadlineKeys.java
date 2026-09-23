package com.contextswitcher.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.stage.Window;
import jfx.incubator.scene.control.input.FunctionTag;
import jfx.incubator.scene.control.richtext.RichTextArea;
import org.jspecify.annotations.Nullable;

/// Readline (bash/emacs) cursor keys in the app's text inputs: Ctrl+A/Ctrl+E
/// jump to the start/end of the line, Ctrl+B/Ctrl+F walk a character, Alt+B/
/// Alt+F a word, and Ctrl+D/H/K/U/W plus Alt+D delete around the caret — the
/// chords a shell user's fingers already know.
///
/// Off by default and switched by the `readlineKeys` setting: turning it on
/// takes Ctrl+A away from select-all and Ctrl+F away from the find bar *while
/// a text input has focus*, which must be the user's own decision.
///
/// Installed as one **capture-phase scene filter per window** rather than as a
/// handler per control: a filter sees the key before the focused control's own
/// behavior (and before the scene's accelerators), and hanging it off
/// [Window#getWindows()] reaches every dialog the app ever opens without each
/// dialog having to remember. The filter is registered unconditionally and
/// reads the [#setEnabled] flag per keystroke, so a settings save flips the
/// mode live, with no re-installation and no restart.
///
/// Only text inputs are affected — the focus owner must be a
/// [TextInputControl] or the [RichTextArea] editor. The embedded terminal
/// keeps every control key (the remote shell has real readline anyway).
// [impl->dsn~readline-keys~1]
public final class ReadlineKeys {

    /// One chord: what it does to a plain text control, and the [RichTextArea]
    /// function tag that is its equivalent in the rich editor (null where the
    /// editor has no such function — Ctrl+K).
    private record Binding(Consumer<TextInputControl> plain, @Nullable FunctionTag rich) {
    }

    /// Marks a scene whose filter is already installed, so the explicit
    /// [#install(Scene)] and the window-list hook cannot double up.
    private static final Object INSTALLED = new Object();

    private static final Map<KeyCombination, Binding> BINDINGS = bindings();

    private static volatile boolean enabled;

    private ReadlineKeys() {
    }

    /// Turns the chords on or off for the whole application. Called at startup
    /// and again after every settings save.
    public static void setEnabled(boolean on) {
        enabled = on;
    }

    /// Installs the filter on `scene`, once. Called explicitly for the main
    /// window's scene **before** its Ctrl+F find filter, so that inside a text
    /// input Ctrl+F moves the caret instead of opening the find bar (filters
    /// run in registration order); everywhere else find still wins.
    public static void install(Scene scene) {
        if (scene.getProperties().putIfAbsent(INSTALLED, Boolean.TRUE) != null) {
            return;
        }
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> handle(scene, event));
    }

    /// Installs the filter on every window that exists now and every window
    /// opened later — the dialogs (settings, add task, link…) included.
    public static void installEverywhere() {
        Window.getWindows().forEach(ReadlineKeys::installOn);
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                change.getAddedSubList().forEach(ReadlineKeys::installOn);
            }
        });
    }

    private static void installOn(Window window) {
        // A window enters the list when it is shown, and showing needs a scene.
        if (window.getScene() != null) {
            install(window.getScene());
        }
    }

    /// Applies the chord `event` names to whatever text control has the focus,
    /// and consumes the event so the control's own binding never sees it.
    private static void handle(Scene scene, KeyEvent event) {
        if (!enabled) {
            return;
        }
        // Walk up from the focus owner: a control's focus may sit on an inner
        // node (the rich editor's content flow), and the chord belongs to the
        // text control around it.
        Node node = scene.getFocusOwner();
        while (node != null && !(node instanceof TextInputControl)
                && !(node instanceof RichTextArea)) {
            node = node.getParent();
        }
        if (node == null) {
            return;
        }
        for (Map.Entry<KeyCombination, Binding> entry : BINDINGS.entrySet()) {
            if (!entry.getKey().match(event)) {
                continue;
            }
            if (node instanceof TextInputControl field) {
                entry.getValue().plain().accept(field);
                event.consume();
            } else if (entry.getValue().rich() != null) {
                ((RichTextArea) node).execute(entry.getValue().rich());
                event.consume();
            }
            return;
        }
    }

    /// The chord table, in readline's own naming. `Ctrl+P`/`Ctrl+N` are left
    /// out on purpose: in bash they walk the *history*, which a text box has
    /// none of, and there is no kill ring, so `Ctrl+Y` (yank) is out too —
    /// what the kill chords remove comes back with Ctrl+Z instead.
    private static Map<KeyCombination, Binding> bindings() {
        Map<KeyCombination, Binding> map = new LinkedHashMap<>();
        // beginning-of-line / end-of-line. TextInputControl.home()/end() move to
        // the start/end of the *whole text*, which is Ctrl+Home territory, so the
        // line bounds are computed here — on logical lines, as readline has them.
        map.put(ctrl(KeyCode.A), new Binding(
                field -> field.positionCaret(lineStart(text(field), field.getCaretPosition())),
                RichTextArea.Tag.MOVE_TO_LINE_START));
        map.put(ctrl(KeyCode.E), new Binding(
                field -> field.positionCaret(lineEnd(text(field), field.getCaretPosition())),
                RichTextArea.Tag.MOVE_TO_LINE_END));
        // backward-char / forward-char
        map.put(ctrl(KeyCode.B),
                new Binding(TextInputControl::backward, RichTextArea.Tag.MOVE_LEFT));
        map.put(ctrl(KeyCode.F),
                new Binding(TextInputControl::forward, RichTextArea.Tag.MOVE_RIGHT));
        // backward-word / forward-word (forward-word lands *after* the word)
        map.put(alt(KeyCode.B),
                new Binding(TextInputControl::previousWord, RichTextArea.Tag.MOVE_WORD_PREVIOUS));
        map.put(alt(KeyCode.F),
                new Binding(TextInputControl::endOfNextWord, RichTextArea.Tag.MOVE_WORD_NEXT_END));
        // delete-char / backward-delete-char
        map.put(ctrl(KeyCode.D),
                new Binding(TextInputControl::deleteNextChar, RichTextArea.Tag.DELETE));
        map.put(ctrl(KeyCode.H),
                new Binding(TextInputControl::deletePreviousChar, RichTextArea.Tag.BACKSPACE));
        // kill-line / unix-line-discard
        map.put(ctrl(KeyCode.K), new Binding(ReadlineKeys::killToLineEnd, null));
        map.put(ctrl(KeyCode.U), new Binding(ReadlineKeys::killToLineStart,
                RichTextArea.Tag.DELETE_PARAGRAPH_START));
        // unix-word-rubout / kill-word
        map.put(ctrl(KeyCode.W), new Binding(ReadlineKeys::killPreviousWord,
                RichTextArea.Tag.DELETE_WORD_PREVIOUS));
        map.put(alt(KeyCode.D), new Binding(ReadlineKeys::killNextWord,
                RichTextArea.Tag.DELETE_WORD_NEXT_END));
        return map;
    }

    private static KeyCombination ctrl(KeyCode code) {
        return new KeyCodeCombination(code, KeyCombination.CONTROL_DOWN);
    }

    private static KeyCombination alt(KeyCode code) {
        return new KeyCodeCombination(code, KeyCombination.ALT_DOWN);
    }

    /// `Ctrl+K`: from the caret to the end of the line — or, standing at the
    /// end already, the line break itself, so repeated Ctrl+K eats the text
    /// below like it does in a shell's multi-line buffer.
    private static void killToLineEnd(TextInputControl field) {
        int caret = field.getCaretPosition();
        int end = lineEnd(text(field), caret);
        if (end == caret) {
            end = Math.min(caret + 1, field.getLength());
        }
        if (end > caret) {
            field.deleteText(caret, end);
        }
    }

    /// `Ctrl+U`: from the start of the line to the caret.
    private static void killToLineStart(TextInputControl field) {
        int caret = field.getCaretPosition();
        int start = lineStart(text(field), caret);
        if (start < caret) {
            field.deleteText(start, caret);
        }
    }

    /// `Ctrl+W`: the whitespace-delimited word before the caret — bash's
    /// `unix-word-rubout`, which takes `foo.bar` whole, unlike the
    /// punctuation-aware Ctrl+Backspace.
    private static void killPreviousWord(TextInputControl field) {
        int caret = field.getCaretPosition();
        int start = wordStart(text(field), caret);
        if (start < caret) {
            field.deleteText(start, caret);
        }
    }

    /// `Alt+D`: up to the end of the next word — punctuation-aware, the
    /// counterpart of Alt+F, so it deletes exactly what Alt+F would skip.
    private static void killNextWord(TextInputControl field) {
        int caret = field.getCaretPosition();
        field.endOfNextWord();
        int end = field.getCaretPosition();
        field.positionCaret(caret);
        if (end > caret) {
            field.deleteText(caret, end);
        }
    }

    /// The index just after the line break preceding `caret` (0 in the first
    /// line) — a single-line field always answers 0.
    static int lineStart(String text, int caret) {
        return text.lastIndexOf('\n', caret - 1) + 1;
    }

    /// The index of the line break at or after `caret`, or the text's end.
    static int lineEnd(String text, int caret) {
        int end = text.indexOf('\n', caret);
        return end < 0 ? text.length() : end;
    }

    /// The start of the whitespace-delimited word before `caret`: skip the
    /// whitespace under the caret, then the word in front of it.
    static int wordStart(String text, int caret) {
        int start = Math.min(caret, text.length());
        while (start > 0 && Character.isWhitespace(text.charAt(start - 1))) {
            start--;
        }
        while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
            start--;
        }
        return start;
    }

    /// A control's text, never null — `setText(null)` is legal on a text control.
    private static String text(TextInputControl field) {
        return Objects.requireNonNullElse(field.getText(), "");
    }
}
