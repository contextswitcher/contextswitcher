package com.contextswitcher.tasks;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~empty-category-gitkeep~1]
class GitkeepTest {

    @Test
    void ensureAddsPlaceholderToEmptyCategory(@TempDir Path root) throws Exception {
        Path category = Files.createDirectories(root.resolve("jabref"));

        Gitkeep.ensure(root, category);

        assertThat(Files.exists(category.resolve(".gitkeep"))).isTrue();
    }

    @Test
    void ensureLeavesTheTasksRootAlone(@TempDir Path root) {
        Gitkeep.ensure(root, root);

        assertThat(Files.exists(root.resolve(".gitkeep"))).isFalse();
    }

    @Test
    void ensureSkipsAFolderThatAlreadyHasARealFile(@TempDir Path root) throws Exception {
        Path category = Files.createDirectories(root.resolve("jabref"));
        Files.writeString(category.resolve("task.md"), "x");

        Gitkeep.ensure(root, category);

        assertThat(Files.exists(category.resolve(".gitkeep"))).isFalse();
    }

    @Test
    void dropRemovesPlaceholderOnceARealFileLands(@TempDir Path root) throws Exception {
        Path category = Files.createDirectories(root.resolve("jabref"));
        Files.writeString(category.resolve(".gitkeep"), "");
        Files.writeString(category.resolve("task.md"), "x");

        Gitkeep.drop(root, category);

        assertThat(Files.exists(category.resolve(".gitkeep"))).isFalse();
        assertThat(Files.exists(category.resolve("task.md"))).isTrue();
    }

    @Test
    void dropKeepsPlaceholderWhileTheFolderIsStillEmpty(@TempDir Path root) throws Exception {
        Path category = Files.createDirectories(root.resolve("jabref"));
        Files.writeString(category.resolve(".gitkeep"), "");

        Gitkeep.drop(root, category);

        assertThat(Files.exists(category.resolve(".gitkeep"))).isTrue();
    }

    @Test
    void isEmptyTreatsALoneGitkeepAsEmpty(@TempDir Path root) throws Exception {
        Path category = Files.createDirectories(root.resolve("jabref"));
        Files.writeString(category.resolve(".gitkeep"), "");

        assertThat(Gitkeep.isEmpty(category)).isTrue();

        Files.writeString(category.resolve("task.md"), "x");
        assertThat(Gitkeep.isEmpty(category)).isFalse();
    }
}
