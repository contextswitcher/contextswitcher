---
status: accepted
date: 2026-08-24
decision-makers: Oliver Kopp
---

# Everforest UI as an AtlantaFX Variable Override, Not a Compiled Theme

## Context and Problem Statement

[MADR 0024](0024-everforest-palette-for-the-terminal-mirror.md) gave the terminal mirror the Everforest palette and rejected Everforest for the JavaFX UI, on the grounds that AtlantaFX 2.0.1 ships only Primer/Nord/Cupertino/Dracula and an Everforest UI would mean hand-authoring a complete theme.

That premise was wrong, and measurably so.
`nord-light.css` is 172 KB, but its `.root` block holds **113 lookup variables** and the remaining 168 KB contain **zero** literal colours — 1091 colour references, every one of them resolved through those variables.
Fifty of the 113 are five ten-step ramps generated from one seed each, so the authoring surface is roughly five seeds plus sixty semantic mappings.

That reopens the question this record answers: how should an Everforest UI be built, given it is now clearly affordable?

## Considered Options

* **Append a second `.root` block to the base theme's stylesheet** at runtime, and set the result as the user-agent stylesheet
* **Compile a real theme from [`mkpaz/atlantafx-sample-theme`](https://github.com/mkpaz/atlantafx-sample-theme)** — the sanctioned SASS starter — and depend on the built artifact
* **Layer the override as a per-scene stylesheet** beside `main.css`
* Keep the UI on Nord (status quo of MADR 0024)

## Decision Outcome

Chosen option: **append a second `.root` block, as the user-agent stylesheet**.

CSS gives the last declaration of equal specificity, so an appended `.root` wins over the theme's own, and since nothing outside that block is a literal colour, the recolouring is total.
The base theme still supplies all structure, which means AtlantaFX upgrades are inherited rather than re-merged — the failure mode of a forked SASS theme.

Per-scene layering was rejected on a fact about this app rather than a preference: only the main window adds a scene stylesheet, so every dialog — settings, delete confirmation, add-task — would have kept the base palette.
The user-agent stylesheet is the one lever that reaches all of them, and it takes a single URL, which is why the concatenation is written to a temp file (one per variant, built once) rather than passed as two stylesheets.

The compiled SASS theme is the better artifact for *publishing* and remains the path if this graduates to a standalone repo; it is the wrong first step for finding out whether an Everforest UI is worth having, which needs an hour and a look, not a toolchain.

### Consequences

* Good, because AtlantaFX's own contrast structure survives the swap: semantic variables keep reading the same ramp positions, so the theme's intent is inherited rather than re-invented.
* Good, because a forgotten variable falls back to the Nord value of the *matching mode* — wrong hue, right lightness — and a build failure falls back to the plain base theme.
* Good, because the palette lives in one generator (`scripts/everforest-atlantafx.py`) that ends in a WCAG AA check, so a contrast regression fails at generation rather than on a user's screen.
* Bad, because it depends on AtlantaFX keeping its stylesheets free of literal colours — asserted by a unit test, which fails loudly if a future release inlines any.
* Bad, because it writes a temp file at startup; a read-only or full temp directory falls back to the base theme rather than the chosen one.
* Neutral, because the result is not a distributable AtlantaFX theme. Publishing one is a later decision, on evidence this prototype provides.

## More Information

This supersedes only the *UI* half of MADR 0024 — the terminal palette, its `bg3`/`fg` mapping, and the ANSI 0–15 ceiling recorded there all still stand.
Palette values: [sainnhe/everforest `palette.md`](https://github.com/sainnhe/everforest/blob/master/palette.md), Dark Medium and Light Medium.
