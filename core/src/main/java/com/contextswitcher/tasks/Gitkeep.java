package com.contextswitcher.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Keeps empty category folders representable in git. git cannot track an
/// empty directory, so a `.gitkeep` placeholder holds an otherwise-empty
/// category (it commits and syncs to other machines); the placeholder is
/// removed the moment a real file lands next to it. The invariant maintained
/// by [#ensure] / [#drop] is: *an empty category folder holds exactly one
/// `.gitkeep`, a populated one holds none.* The tasks **root** is never
/// touched — only its subfolders (categories).
// [impl->dsn~empty-category-gitkeep~1]
public final class Gitkeep {

    /// The placeholder file name.
    public static final String NAME = ".gitkeep";

    private Gitkeep() {
    }

    /// Adds a `.gitkeep` to `dir` when it is an empty category folder (a
    /// subfolder of `tasksRoot`, never the root itself). No-op when it already
    /// has one, holds a real file, or is not a directory under the root.
    public static void ensure(Path tasksRoot, @Nullable Path dir) {
        if (!isCategory(tasksRoot, dir) || !Files.isDirectory(dir)) {
            return;
        }
        try {
            if (isEmpty(dir) && !Files.exists(dir.resolve(NAME))) {
                TextFiles.write(dir.resolve(NAME), "");
            }
        } catch (IOException e) {
            Logger.debug("Cannot add .gitkeep to {}: {}", dir, e.getMessage());
        }
    }

    /// Removes `dir`'s `.gitkeep` once the folder holds a real file, so the
    /// placeholder never lingers next to actual tasks. No-op for the root.
    public static void drop(Path tasksRoot, @Nullable Path dir) {
        if (!isCategory(tasksRoot, dir)) {
            return;
        }
        try {
            if (Files.isDirectory(dir) && !isEmpty(dir)) {
                Files.deleteIfExists(dir.resolve(NAME));
            }
        } catch (IOException e) {
            Logger.debug("Cannot drop .gitkeep from {}: {}", dir, e.getMessage());
        }
    }

    /// A category folder counts as empty when it holds nothing but a possible
    /// `.gitkeep` — the placeholder itself does not populate it.
    static boolean isEmpty(Path dir) throws IOException {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.noneMatch(p -> !p.getFileName().toString().equals(NAME));
        }
    }

    /// A path is a category when it is a non-null subfolder of the tasks root
    /// (not the root itself).
    private static boolean isCategory(Path tasksRoot, @Nullable Path dir) {
        return dir != null && !dir.equals(tasksRoot) && dir.startsWith(tasksRoot);
    }
}
