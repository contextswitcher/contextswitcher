---
status: accepted
date: 2026-08-23
decision-makers: Oliver Kopp
---

# Everforest Palette for the Terminal Mirror, Nord for the UI

## Context and Problem Statement

The theme setting (`dsn~theme-select~2`) recolors the whole UI, but the terminal mirror stayed at a hard-coded `#1e1e1e` ground — a light app framing a dark terminal.
The JavaFX chrome rides AtlantaFX's CSS variables and needs no palette of its own; the terminal cannot, because JediTermFX takes a `SettingsProvider` with Java colors and a 16-entry ANSI palette.
So the terminal needs a named light/dark palette pair chosen on purpose, and the question came with a candidate: should [Everforest](https://github.com/sainnhe/everforest) be used — for the terminal, and for the UI as well?

## Considered Options

* **Everforest for the terminal, AtlantaFX Nord for the UI** — one palette per surface, each from a source that ships both a light and a dark variant
* **Everforest for both** — one identity across the whole window
* **Nord for both** — the UI theme's palette extended into the terminal
* **Neutral light ground** (`#ffffff` + darkened xterm defaults) beside the current `#1e1e1e`

## Decision Outcome

Chosen option: **Everforest (medium) in the terminal, AtlantaFX Nord in the UI**.

Amended 2026-09-12: once the UI gained Everforest themes of its own, the terminal is drawn in Everforest only under those; the plain Nord themes get a plain black or white terminal, since users who never chose Everforest found its cream ground surprising.

Everforest is a terminal-first palette that publishes *both* a dark and a light variant, tuned as a pair, with the light one designed to be a light theme rather than a dark theme inverted — exactly the missing half.
Its low contrast is a deliberate fit for a pane one reads all day.

Everforest for the UI as well was rejected on cost: AtlantaFX 2.0.1 ships Primer, Nord, Cupertino, and Dracula only, so an Everforest UI means hand-authoring a complete AtlantaFX theme — hundreds of CSS variables to write and to keep in step with the library — for a hue change.
> **Superseded by [MADR 0025](0025-everforest-ui-as-an-atlantafx-variable-override.md) (2026-08-24):** the cost estimate in this paragraph was wrong — an AtlantaFX theme resolves every colour through 113 `.root` variables and holds no literal colour outside that block, so the UI is recoloured by appending one `.root` block, not by authoring a theme. The terminal half of this record stands.
Nord for both fails on the other side: Nord has no official light terminal variant, so the light half would have to be invented, which is the same authoring problem in the place where a palette is easiest to get wrong.
The neutral option costs least of all but leaves the terminal without an identity and still needs its light ANSI values picked by hand.

Two palettes in one window is a real cost, accepted: the terminal is a bounded, high-contrast-boundary box, the two are matched in *lightness* (which is what makes a light UI/dark terminal look broken), and only the hue differs.

### Consequences

* Good, because both variants come from an upstream that maintains them; upgrades are a table of hex values, not a design task.
* Good, because the palette is one class (`TerminalSettings`), unit-testable without a running UI.
* Bad, because the terminal's green-warm hues sit beside the UI's blue-cold ones.
* Bad, because only ANSI 0–15 is ours — a remote program emitting 256-index or 24-bit color keeps its dark colors on the light ground, and the fix lives on the remote (`dsn~terminal-theme~2` records this ceiling).

## More Information

Palette values: [sainnhe/everforest `palette.md`](https://github.com/sainnhe/everforest/blob/master/palette.md), Dark Medium and Light Medium columns.
