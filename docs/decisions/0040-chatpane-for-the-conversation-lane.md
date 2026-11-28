---
status: proposed
date: 2026-11-28
decision-makers: Oliver Kopp
---

# ChatPane for the conversation lane, fed from the transcript

## Context and Problem Statement

In the terminal mirror the user's own messages and Claude's replies are one stream of rendered text; after a long message of one's own it is hard to see where the reply starts (Oliver, 2026-11-28, with a screenshot).
He asked for a second view next to the terminal ("Rendered Terminal") and how to get there: tell Claude to format its output differently, intercept the terminal and render it nicely, use JabRef's ChatPane, pull in JavaFX WebView, or change the UI technology.
Two questions: where does the view get who said what, and what draws it?

## Decision Drivers

* The speaker of every message must be certain, not guessed.
* Markdown as Claude wrote it (the screen has it rendered away, `dsn~terminal-markdown-copy~1`).
* Themed like the rest of the app, no second look-and-feel, no new runtime.
* Works for every session, also ones started outside the app; nothing to configure in Claude.
* License compatible with MIT.

## Considered Options

* Source of the conversation
  * The session transcript (`~/.claude/projects/<cwd>/<session>.jsonl`)
  * Intercepting the terminal stream and parsing the screen
  * Instructing Claude to mark up its output
* Rendering
  * JabRef ChatPane (`org.jabref:chatpane`)
  * JavaFX WebView with an HTML chat page
  * An own view on `RichTextArea`
  * Another UI technology (web UI, Compose)

## Decision Outcome

Chosen option: **the transcript as source, drawn by ChatPane**, because the transcript already names the speaker of every line and holds the Markdown source, the app already reads it over the side channel (`ClaudeReplies`, `ClaudeTranscript`), and ChatPane is a JavaFX control built for exactly this on the JavaFX 26 and `RichTextArea` the app already ships.

### Consequences

* Good, because the mirror stays what it is; the conversation is a second lane, and no Claude setting or prompt changes.
* Good, because it costs one dependency of 100 kB plus commonmark, and no new runtime.
* Bad, because ChatPane exists only as a snapshot (`0.1.0-SNAPSHOT` on Central's snapshot repository): pinned to one timestamped build, like ShellFX (MADR 0032).
* Bad, because the view is as fresh as its last read (status edges and **Refresh**), not a live stream; the terminal remains the live view.
* ChatPane colors itself from Modena's lookups (`-fx-accent`, `-fx-base`, …), which AtlantaFX does not define, and AtlantaFX fixes list cells at `3em`: `main.css` maps the lookups to the theme's and frees the cell size.

### Confirmation

`ClaudeConversationTest` pins the remote command and the parsing; `ConversationPaneUiTest` shows a transcript in the pane under an AtlantaFX theme and checks **Reply**.

## Pros and Cons of the Options

### The session transcript

* Good, because role, Markdown source and time are data, per message.
* Good, because already read for *Copy reply* and the voice tour.
* Bad, because its format is Claude Code's internal one and may change.

### Intercepting the terminal stream

* Bad, because Claude Code draws in the alternate screen buffer and redraws at will: the stream is paint, not messages, and the Markdown is already gone.
* Bad, because who said what would be guessed from colors and gutters that change between Claude Code versions.

### Instructing Claude to mark up its output

* Bad, because it changes every session's prompt, costs tokens, and does nothing for the user's own messages.

### JabRef ChatPane

* Good, because bubbles, Markdown, selectable text, per-message actions and theme-following colors are there.
* Good, because Apache-2.0, and its maintainers are reachable.
* Bad, because early (snapshot only).

### JavaFX WebView

* Good, because HTML renders any Markdown, tables included.
* Bad, because it adds `javafx-web` (WebKit, about 60 MB per platform) and a second styling world to keep in step with the theme.

### An own view on `RichTextArea`

* Bad, because it rebuilds what ChatPane already has.

### Another UI technology

* Bad, because nothing in this problem needs it; the whole app would be rewritten for one view.
