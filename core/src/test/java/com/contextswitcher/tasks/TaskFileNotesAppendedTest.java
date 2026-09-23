package com.contextswitcher.tasks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-create-local-title~1]
class TaskFileNotesAppendedTest {

    @Test
    void appendsUnderTheExistingNotesHeading() {
        String content = TaskFileParser.newTaskContent("t", null, null, false);

        assertThat(TaskFileParser.withNotesAppended(content, "  first line\nsecond line\n"))
                .isEqualTo("---\ntitle: t\nstatus: active\n---\n\n# Notes\n\nfirst line\nsecond line\n");
    }

    @Test
    void appendsAfterNotesAlreadyWritten() {
        String content = "---\ntitle: t\n---\n\n# Notes\n\nmine\n";

        assertThat(TaskFileParser.withNotesAppended(content, "description"))
                .isEqualTo("---\ntitle: t\n---\n\n# Notes\n\nmine\n\ndescription\n");
    }

    @Test
    void addsTheHeadingWhenTheBodyHasNone() {
        assertThat(TaskFileParser.withNotesAppended("---\ntitle: t\n---\nbody\n", "description"))
                .isEqualTo("---\ntitle: t\n---\nbody\n\n# Notes\n\ndescription\n");
    }

    @Test
    void blankTextChangesNothing() {
        String content = "---\ntitle: t\n---\n";

        assertThat(TaskFileParser.withNotesAppended(content, " \n")).isSameAs(content);
    }
}
