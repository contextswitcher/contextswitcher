---
status: accepted
date: 2026-10-11
decision-makers: Oliver Kopp
---

# ExtendedTable for the host settings table

## Context and Problem Statement

Everything the app knows about a remote host used to be spread over separate settings: `remotes` (a text list), `inactiveRemotes` (a second text list) and one global `refactoringMinerHome`.
Two machines rarely share a directory layout, so a field naming one directory "on the remotes" was wrong for every setup with two hosts, and the wizard proposed one host's workspaces directory on another (field report 2026-10-11: `/data/kopp` from swkgpu1 proposed on nixos, which has no `/data`).
Oliver, 2026-10-11: the settings should be a table per host — "can we try to use https://github.com/Maran23/extended-table".

The settings form is generated from `SettingsCatalog` by an own small renderer (MADR 0015); the hosts need a control that edits a list of records with four fields each: destination, on/off, workspaces directory, RefactoringMiner directory.

## Decision Drivers

* Edit in place: a host row is one line, and adding or switching off a host should not open a sub-dialog.
* A required field (the destination) must be marked when left empty.
* Small and themed like the rest: AtlantaFX colors, `Styles.SMALL`, no second look-and-feel.
* License compatible with MIT (no strong copyleft).
* Java 25 / JavaFX 26, no module system needed.

## Considered Options

* ExtendedTable (`tools.maran:extended-table`)
* A plain JavaFX `TableView` with hand-written editable cells
* One row of text fields per host, like the tag palette's `TagListEditor`
* ControlsFX `TableView2` / `SpreadsheetView`

## Decision Outcome

Chosen option: **ExtendedTable**, because it is a `TableView` subclass whose columns take a read and a write function and a validator — exactly the record editing the host table needs, without hand-written cell factories — and Oliver asked for it.
It is MIT-licensed, on Maven Central (`tools.maran:extended-table:1.0.1`), needs Java 25+ and JavaFX 25+, and pulls in no runtime dependencies (JavaFX is `provided`).
Its author also maintains `svgnode` and `svg-materialdesign`, which the app already uses (MADR 0010).

The table is one control in the generated form (`HostTable`, catalog type `HOSTS`); the rest of the form is unchanged.

### Consequences

* Good, because a host is one editable row: *Destination* validated non-blank, *Active* a check box, the two directories plain text cells.
* Good, because the per-host RefactoringMiner **Set up** fits naturally as an action on the selected row.
* Good, because filtering and fixed columns come for free should the table grow (many hosts).
* Neutral, because `getItems()` returns only the visible rows — rows are added and removed through `getBackingItems()`.
* Bad, because the library is young (1.0.x, few users): a breaking change or abandonment would mean replacing one control — a contained risk, the `HostTable` class is the only user.

### Confirmation

`SettingsFormRoundTripUiTest` round-trips a settings file with two hosts through the form unchanged; `dsn~host-table~1` traces the table to its requirement.

## Pros and Cons of the Options

### ExtendedTable

* Good, because read/write functions and validators per column replace cell factories.
* Good, because MIT, no transitive dependencies, same Java/JavaFX baseline.
* Bad, because it is a young library with a small user base.

### A plain JavaFX `TableView` with hand-written editable cells

* Good, because no dependency at all.
* Bad, because editable text and check-box cells that commit on focus loss, plus validation marking, are a known source of fiddly code (`TextFieldTableCell` commits only on Enter).

### One row of text fields per host, like `TagListEditor`

* Good, because it matches the existing tag editor.
* Bad, because four fields per row with no column headers read as a wall of boxes, and alignment across rows is manual.

### ControlsFX `TableView2` / `SpreadsheetView`

* Good, because mature.
* Bad, because a spreadsheet model is heavier than four columns need, and ControlsFX is a large dependency for one table.

## More Information

Settings files from before the table (`remotes`, `inactiveRemotes`, `refactoringMinerHome`) are read into it, and the next save writes `hosts:` in their place (`dsn~host-table~1`).
