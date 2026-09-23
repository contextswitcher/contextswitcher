---
status: accepted
date: 2026-07-20
decision-makers: Oliver Kopp
---

# Catalog-Driven Settings Form, Raw YAML as the Source of Truth

## Context and Problem Statement

The settings editor started as a single raw `settings.yaml` `TextArea` (`dsn~settings-editor~4`).
Editing YAML by hand is error-prone for non-power users, and the field set will keep growing.
We want high-level controls (checkboxes, typed fields, a proper tag editor, per-section grouping) generated from a catalog rather than hand-laid per setting, without losing the raw view — and without the form silently discarding comments or keys the app does not yet understand.

## Considered Options

* **Own catalog-driven form** — a `SettingsCatalog` describing each option, a small renderer mapping option type → JavaFX control, and `YamlPatch` merging changed keys back into the raw text
* **FormsFX** (`com.dlsc.formsfx:formsfx-core`, Apache-2.0) — declarative form model + renderer
* ControlsFX `PropertySheet` — reflective property grid
* Hand-laid JavaFX controls in a `GridPane`, no catalog
* Raw `TextArea` only (status quo)

## Decision Outcome

Chosen option: **our own catalog-driven form**, with the raw `settings.yaml` text as the data source of truth.

FormsFX was tried first and removed.
Its default `FormRenderer` gave an unworkable layout (a tiny label column against a huge control column) and offers no clean way to put the label above the control, right-align controls, add a hover highlight, or host a bespoke tag (name + color) editor.
Working around that meant supplying our own renderer and binding our own controls to FormsFX's `userInput` properties — at which point FormsFX contributed only `Integer.parseInt` and a checkbox boolean, not worth a 2023-era dependency plus the openjfx-exclusion hack its bundled JavaFX required against our target-platform-patched JavaFX 26, plus its unused i18n/validation machinery.

The substance is ours regardless:

* `SettingsCatalog.OPTIONS` — one entry per key `AppSettings` reads (label, group, type, help). Adding a setting is a getter in `AppSettings` plus one catalog entry, not another hand-wired control.
* A ~40-line renderer maps each option type to a control (text field, checkbox, multi-line list area) and lays each out as a labelled, hover-highlighted row grouped into sections. The tag palette — the one value that is not a flat scalar or line list — gets a purpose-built `TagListEditor` (a row per tag: name field + `ColorPicker` + remove, plus "Add tag").
* `YamlPatch` keeps the raw text the source of truth: on Save (or when toggling to the raw view) only the keys whose values actually changed are patched back into that text, replacing a single top-level key's block in place and leaving every other line — comments, key order, and keys the app does not know about — byte-for-byte untouched.

A **View raw** toggle shows that same text for full-text editing (with the **F1** field reference); switching back reparses it and rebuilds the form.
When the file on disk is too malformed to parse, the dialog opens straight in a raw-only fallback for repair.

### Consequences

* Good, because the settings dialog is a catalog-driven form with full control over layout — new scalar/boolean/list options are declarative, and the raw view stays for power users.
* Good, because comments and unknown/future keys survive a form edit (raw-as-source-of-truth via `YamlPatch`), which a full re-dump could not guarantee.
* Good, because there is no settings-only third-party UI dependency to track or work around; the form is plain JavaFX + AtlantaFX like the rest of the UI.
* Good, because `YamlPatch` is plain string logic covered by unit tests (`YamlPatchTest`); the form/raw toggle and tag editor are UI behavior for a TestFX test rather than the checklist.
* Neutral, because `YamlPatch` handles only flat top-level keys — sufficient for `settings.yaml`, which is a flat mapping; nested structures would need a comment-preserving YAML round-tripper.
* Bad, because a bespoke control type (e.g. the tag editor) is more code than a declarative field would be; mitigated by keeping the renderer small and driven by the catalog.
