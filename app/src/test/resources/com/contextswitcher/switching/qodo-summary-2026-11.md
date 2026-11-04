<h3>Code Review by Qodo</h3>

<code>🐞 Bugs (6)</code>

<img src="https://www.qodo.ai/wp-content/uploads/2025/11/light-grey-line.svg" height="10%" alt="Grey Divider">

<br/>

<img src="https://img.shields.io/badge/Medium-634FD1?style=flat-square" height="20px" alt="Remediation recommended">

<details>
<summary>  1.  Missing cross-references lose their column <code>🐞 Bug</code> <code>⭐ New</code></summary>

<br/>

> <details>
><summary>Description</summary>
><br/>
>
><pre>
><b><i>restrictToBstFields</i></b> builds its visibility predicate solely from <b><i>ENTRY</i></b> declarations, but the
>style renderer reads <b><i>crossref</i></b> even when it is not declared there. The shipped <b><i>abbrv.bst</i></b> uses
><b><i>crossref</i></b> without declaring it, so selecting that style hides the cross-reference column when the
>consistency check reports a missing required cross-reference.
></pre>
></details>

> <details>
><summary>Code</summary>
><br/>
>
><code>[jabgui/src/main/java/org/jabref/gui/consistency/ConsistencyCheckDialogViewModel.java[R151-152]](https://github.com/JabRef/jabref/pull/17710/files#diff-73f06a1d153250b9083a1877c40a7e8e4d5fac4c9b3fb66becfbd739bc6aa674R151-R152)</code>
>
>```diff
>+            Set<String> entryFields = new BstVM(bstFile).getEntryFields();
>+            shownFields.set(field -> isReadByStyle(entryFields, field));
>```
></details>

> <details>
><summary>Agent prompt</summary>
><br/>
>
>```
>The issue below was found during a code review. Follow the provided context and guidance below and implement a solution
>
>## Issue description
>The style renderer reads `crossref` implicitly, but filtering solely by `ENTRY` declarations hides a reported cross-reference column for styles such as `abbrv.bst`.
>
>## Fix Focus Areas
>- jabgui/src/main/java/org/jabref/gui/consistency/ConsistencyCheckDialogViewModel.java[149-165]
>- jablib/src/main/java/org/jabref/logic/bst/BstVMVisitor.java[78-89]
>
>## Recommended Fix
>Keep a reported cross-reference column visible when restricting to a style that reads it implicitly. Add a test using a style without `crossref` in `ENTRY` and a result that reports a missing required cross-reference.
>```
> <code>ⓘ Copy this prompt and use it to remediate the issue with your preferred AI generation tools</code>
></details>

<hr/>
</details>


<details><summary><ins><strong>View medium (5)</strong></ins></summary><br/>
<details>
<summary>  2.  Year differences disappear for date styles <code>🐞 Bug</code></summary>

<br/>

> <details>
><summary>Description</summary>
><br/>
>
><pre>
><b><i>isReadByStyle</i></b> includes year, month and day when checking a reported date column, but does not make
>those reported columns visible when the style declares <b><i>date</i></b>. If entries differ in <b><i>year</i></b> and the
>selected style declares <b><i>date</i></b> rather than <b><i>year</i></b>, the style can derive its date from that year
>while the consistency dialog hides the year difference.
></pre>
></details>

> <details>
><summary>Code</summary>
><br/>
>
><code>[jabgui/src/main/java/org/jabref/gui/consistency/ConsistencyCheckDialogViewModel.java[R162-165]](https://github.com/JabRef/jabref/pull/17710/files#diff-73f06a1d153250b9083a1877c40a7e8e4d5fac4c9b3fb66becfbd739bc6aa674R162-R165)</code>
>
>```diff
>+        Stream<Field> fieldsSeenByStyle = field == StandardField.DATE
>+                ? Stream.of(StandardField.DATE, StandardField.YEAR, StandardField.MONTH, StandardField.DAY)
>+                : Stream.concat(Stream.of(field), Optional.ofNullable(EntryConverter.FIELD_ALIASES.get(field)).stream());
>+        return fieldsSeenByStyle.map(seen -> seen.getName().toLowerCase(Locale.ROOT)).anyMatch(entryFields::contains);
>```
></details>

> <details>
><summary>Agent prompt</summary>
><br/>
>
>```
>The issue below was found during a code review. Follow the provided context and guidance below and implement a solution
>
>## Issue description
>A style declaring `date` can read a value derived from `year`, `month`, or `day`, but the current predicate hides those reported columns.
>## Fix Focus Areas
>- jabgui/src/main/java/org/jabref/gui/consistency/ConsistencyCheckDialogViewModel.java[160-165]
>## Recommended Fix
>When evaluating a reported year, month, or day field, also match a style declaration of `date`. Preserve the existing mapping from reported `date` to style-declared date parts, and test both directions.
>```
> <code>ⓘ Copy this prompt and use it to remediate the issue with your preferred AI generation tools</code>
></details>

<hr/>
</details>


<details>
<summary>  3.  Non-style files hide every field column <code>🐞 Bug</code></summary>

<br/>

> <details>
><summary>Description</summary>
><br/>
>
><pre>
><b><i>restrictToBstFields</i></b> installs an empty field predicate when a parsed file contains no <b><i>ENTRY</i></b>
>command, then displays that file as the selected style. The parser accepts files containing other
>commands without <b><i>ENTRY</i></b>, so choosing such a file hides every reported field column rather than
>rejecting the selection.
></pre>
></details>

> <details>
><summary>Code</summary>
><br/>
>
><code>[jabgui/src/main/java/org/jabref/gui/consistency/ConsistencyCheckDialogViewModel.java[R151-153]](https://github.com/JabRef/jabref/pull/17710/files#diff-73f06a1d153250b9083a1877c40a7e8e4d5fac4c9b3fb66becfbd739bc6aa674R151-R153)</code>
>
>```diff
>+            Set<String> entryFields = new BstVM(bstFile).getEntryFields();
>+            shownFields.set(field -> isReadByStyle(entryFields, field));
>+            bstStyleName.set(bstFile.getFileName().toString());
>```
></details>

> <details>
><summary>Agent prompt</summary>
><br/>
>
>```
>The issue below was found during a code review. Follow the provided context and guidance below and implement a solution
>
>## Issue description
>A file containing valid BST syntax but no `ENTRY` command is accepted as a style and hides all reported field columns.
>## Fix Focus Areas
>- jabgui/src/main/java/org/jabref/gui/consistency/ConsistencyCheckDialogViewModel.java[148-157]
>- jablib/src/main/java/org/jabref/logic/bst/BstVM.java[104-112]
>## Recommended Fix
>Distinguish a missing `ENTRY` command from an intentionally empty field list. Reject a file without `ENTRY` before changing the shown-fields predicate or selected-style name, and show the existing invalid-style error.
>```
> <code>ⓘ Copy this prompt and use it to remediate the issue with your preferred AI generation tools</code>
></details>

<hr/>
</details>


<details>
<summary>  4.  Rows stay sorted by a hidden field <code>🐞 Bug</code></summary>

<br/>

> <details>
><summary>Description</summary>
><br/>
>
><pre>
><b><i>initialize</i></b> binds field-column visibility to the selected style without reconciling the table&#x27;s
>existing sort order. If a user sorts a field column before selecting a style that excludes it, the
>column disappears while its sort remains active, leaving the displayed ordering unexplained.
></pre>
></details>

> <details>
><summary>Code</summary>
><br/>
>
><code>[jabgui/src/main/java/org/jabref/gui/consistency/ConsistencyCheckDialog.java[R95-100]](https://github.com/JabRef/jabref/pull/17710/files#diff-b2b489e0e64471976da1abdfd232907cfc6692128f1d39ad57061bc596cd9530R95-R100)</code>
>
>```diff
>+            // The first two columns (entry type and citation key) are always shown
>+            if (currentIndex >= 2) {
>+                Field field = reportedFields.get(currentIndex - 2);
>+                // [impl->req~ux.consistency.bst-field-columns~1]
>+                tableColumn.visibleProperty().bind(viewModel.shownFieldsProperty().map(shown -> shown.test(field)));
>+            }
>```
></details>

> <details>
><summary>Agent prompt</summary>
><br/>
>
>```
>The issue below was found during a code review. Follow the provided context and guidance below and implement a solution
>
>## Issue description
>Restricting visible columns can leave the table sorted by a column the user can no longer see.
>## Fix Focus Areas
>- jabgui/src/main/java/org/jabref/gui/consistency/ConsistencyCheckDialog.java[88-100]
>## Recommended Fix
>When the shown-field predicate changes, remove newly hidden columns from the table's sort order while preserving sorting on columns that remain visible.
>```
> <code>ⓘ Copy this prompt and use it to remediate the issue with your preferred AI generation tools</code>
></details>

<hr/>
</details>


<details>
<summary>  5.  Exports include fields hidden in the dialog <code>🐞 Bug</code></summary>

<br/>

> <details>
><summary>Description</summary>
><br/>
>
><pre>
><b><i>restrictToBstFields</i></b> changes table-column visibility without passing its field predicate to either
>export writer. After selecting a style and exporting from the same dialog, CSV and TXT files still
>contain every reported field, including columns the user just hid.
></pre>
></details>

> <details>
><summary>Code</summary>
><br/>
>
><code>[jabgui/src/main/java/org/jabref/gui/consistency/ConsistencyCheckDialog.java[99]](https://github.com/JabRef/jabref/pull/17710/files#diff-b2b489e0e64471976da1abdfd232907cfc6692128f1d39ad57061bc596cd9530R99-R99)</code>
>
>```diff
>+                tableColumn.visibleProperty().bind(viewModel.shownFieldsProperty().map(shown -> shown.test(field)));
>```
></details>

> <details>
><summary>Agent prompt</summary>
><br/>
>
>```
>The issue below was found during a code review. Follow the provided context and guidance below and implement a solution
>
>## Issue description
>Selecting a BST style filters the visible table columns but leaves CSV and TXT exports unfiltered.
>## Fix Focus Areas
>- jabgui/src/main/java/org/jabref/gui/consistency/ConsistencyCheckDialog.java[95-100]
>- jabgui/src/main/java/org/jabref/gui/consistency/ConsistencyCheckDialogViewModel.java[218-252]
>- jablib/src/main/java/org/jabref/logic/quality/consistency/BibliographyConsistencyCheckResultWriter.java[112-125]
>## Recommended Fix
>Pass a snapshot of the selected field set into both export paths and use it for headers and row cells, retaining entry type and citation key. Test exports with a style selected and after Show all fields.
>```
> <code>ⓘ Copy this prompt and use it to remediate the issue with your preferred AI generation tools</code>
></details>

<hr/>
</details>


<details>
<summary>  6.  Large style files freeze the dialog <code>🐞 Bug</code></summary>

<br/>

> <details>
><summary>Description</summary>
><br/>
>
><pre>
><b><i>restrictToBstFields</i></b> constructs and parses <b><i>BstVM</i></b> synchronously in the file-chooser callback, then
>traverses the resulting parse tree on the JavaFX event thread. Selecting a large local style blocks
>the entire interface during the unbounded read and parse, unlike the dialog&#x27;s background export
>operations.
></pre>
></details>

> <details>
><summary>Code</summary>
><br/>
>
><code>[jabgui/src/main/java/org/jabref/gui/consistency/ConsistencyCheckDialogViewModel.java[148]](https://github.com/JabRef/jabref/pull/17710/files#diff-73f06a1d153250b9083a1877c40a7e8e4d5fac4c9b3fb66becfbd739bc6aa674R148-R148)</code>
>
>```diff
>+            Set<String> entryFields = new BstVM(bstFile).getEntryFields();
>```
></details>

> <details>
><summary>Agent prompt</summary>
><br/>
>
>```
>The issue below was found during a code review. Follow the provided context and guidance below and implement a solution
>
>## Issue description
>The newly selected style is read, parsed, and traversed on the JavaFX event thread, making the dialog unresponsive for large files.
>## Fix Focus Areas
>- jabgui/src/main/java/org/jabref/gui/consistency/ConsistencyCheckDialogViewModel.java[136-154]
>## Recommended Fix
>Run file reading and BST field extraction in a background task. Apply the resulting predicate and filename on the UI thread, and route read or parse failures to the existing error dialog.
>```
> <code>ⓘ Copy this prompt and use it to remediate the issue with your preferred AI generation tools</code>
></details>

<hr/>
</details>


</details>

<img src="https://www.qodo.ai/wp-content/uploads/2025/11/light-grey-line.svg" height="10%" alt="Grey Divider">

<details><summary><strong>Resolved findings</strong></summary>

<br/>

<details>
<summary>  1.  <s>Style-relevant fields disappear</s> <code>✓ Resolved</code></summary>

<br/>

<!-- qodo-finding-reference
<code>↗ jabgui/src/main/java/org/jabref/gui/consistency/ConsistencyCheckDialogViewModel.java[R149]</code>
-->
> <details>
><summary>Description</summary>
><br/>
>
><pre>
><b><i>restrictToBstFields</i></b> matches reported field names only against literal <b><i>ENTRY</i></b> names, although the
>style renderer resolves aliases and derives year from date. With <b><i>abbrv.bst</i></b>, a bibliography using
><b><i>journaltitle</i></b> or <b><i>date</i></b> can have those values read by the style while their consistency columns are
>hidden.
></pre>
></details>

<hr/>
</details>


</details>

<img src="https://www.qodo.ai/wp-content/uploads/2025/11/light-grey-line.svg" height="10%" alt="Grey Divider">

<!-- qodo-self-review-slot -->



<!-- qodo-daily-tip:start -->

<details>
<summary><strong>Tip of the day</strong></summary>

<br/>

<pre>💡 Did you know, you can commit Qodo&#x27;s fix in one click with committable suggestions (GitHub &amp; GitLab)</pre>

<a href="https://docs.qodo.ai/tips-and-tricks">More tips ↗</a> | <a href="https://app.qodo.ai/configurations?tab=display-preferences">Customize Qodo ↗</a> | <a href="https://docs.qodo.ai">Qodo docs ↗</a>

</details>

<img src="https://www.qodo.ai/wp-content/uploads/2025/11/light-grey-line.svg" height="10%" alt="Grey Divider">
<!-- qodo-daily-tip:end -->


<!-- https://github.com/JabRef/jabref/commit/e27f6a211c75ba66849b573057ad2b2dd31c7d5e -->

<a href="https://www.qodo.ai"><img src="https://www.qodo.ai/wp-content/uploads/2025/03/qodo-logo.svg" width="80" alt="Qodo Logo"></a>
