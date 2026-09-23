package com.contextswitcher.terminal;

import java.util.ArrayList;
import java.util.List;

import com.techsenger.jeditermfx.core.model.hyperlinks.LinkResult;
import com.techsenger.jeditermfx.core.model.hyperlinks.LinkResultItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~terminal-file-links~1]
class FileHyperlinkFilterTest {

    private final List<String> clicked = new ArrayList<>();
    private final FileHyperlinkFilter filter = new FileHyperlinkFilter(clicked::add);

    @Test
    void marksAnAttachmentPathAndHandsItToTheOpener() {
        String line = "saved to /export/home/koppor/.contextswitcher/attachments/img-1-2.png now";

        LinkResult result = filter.apply(line);

        assertThat(result).isNotNull();
        LinkResultItem item = result.getItems().getFirst();
        assertThat(line.substring(item.getStartOffset(), item.getEndOffset()))
                .isEqualTo("/export/home/koppor/.contextswitcher/attachments/img-1-2.png");
        item.getLinkInfo().navigate();
        assertThat(clicked)
                .containsExactly("/export/home/koppor/.contextswitcher/attachments/img-1-2.png");
    }

    @Test
    void marksATildePathAndStopsAtTheSentencePunctuation() {
        LinkResult result = filter.apply("see ~/reports/run.md, then the log.");

        assertThat(result).isNotNull();
        assertThat(result.getItems()).hasSize(1);
        assertThat(FileHyperlinkFilter.pathFor("~/reports/run.md,")).isEqualTo("~/reports/run.md");
    }

    @Test
    void ignoresDirectoriesAndProse() {
        assertThat(filter.apply("cd /data/koppor/contextswitcher and run either/or")).isNull();
    }

    @Test
    void leavesTheDefaultFiltersUrlsAlone() {
        assertThat(filter.apply("https://github.com/JabRef/jabref/blob/main/build.gradle.kts"))
                .isNull();
    }
}
