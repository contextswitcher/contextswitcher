package com.contextswitcher.tasks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-add-link~3]
class OneNoteLinkTest {

    @Test
    void extractsTheOnenoteLineFromTheTwoLineClipboard() {
        String clip = """
                https://onedrive.live.com/view.aspx?resid=D7087%21509417&id=documents&wd=target%28x%29&end
                onenote:https://d.docs.live.net/abc/Doc.one#Sec&section-id={AAA}&page-id={BBB}&end
                """;

        assertThat(OneNoteLink.extract(clip))
                .isEqualTo("onenote:https://d.docs.live.net/abc/Doc.one#Sec&section-id={AAA}&page-id={BBB}&end");
    }

    @Test
    void returnsNullWhenNoOnenoteLinkIsPresent() {
        assertThat(OneNoteLink.extract("https://example.org/page")).isNull();
    }

    /// A link typed or pasted on its own is kept whole — a page in a notebook
    /// whose name has a space would lose its tail to the token match.
    @Test
    void aTypedOnenoteLinkIsKeptWholeSpacesIncluded() {
        String link = "onenote:https://d.docs.live.net/abc/My Notes.one#Sec&section-id={AAA}&end";

        assertThat(OneNoteLink.noteLink("  " + link + " ")).isEqualTo(link);
    }

    @Test
    void aPastedClipboardYieldsItsOnenoteLineAndABrowserUrlNone() {
        assertThat(OneNoteLink.noteLink("https://onedrive.live.com/view.aspx?resid=1\n"
                + "onenote:https://d.docs.live.net/abc/Doc.one#Sec&end"))
                .isEqualTo("onenote:https://d.docs.live.net/abc/Doc.one#Sec&end");
        assertThat(OneNoteLink.noteLink("https://example.org/page")).isNull();
    }
}
