package com.contextswitcher.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/// How the user works with a repository, picked in the `Add category from
/// URL…` dialog. It decides two things about the setup session: whether it
/// clones a fork or the repository itself, and which of the shipped
/// workspace-root `CLAUDE.md` templates it fills in.
///
/// The templates live next to this class as classpath resources. They
/// describe the *workspaces root* — the layout (primary clone plus per-task
/// worktrees), the remotes, the branch bases, the cleanup rules — and leave
/// `<placeholder>`s for what only the clone itself can answer (build and test
/// commands, PR conventions). The repository's own `CLAUDE.md` inside the
/// checkout keeps the project conventions and is untouched.
// [impl->dsn~claude-md-templates~1]
public enum RepoType {

    /// No write access upstream: work in a personal fork, PR upstream.
    FORK_PR_UPSTREAM("Fork, PR upstream (no write access)", "fork-pr-upstream", true),
    /// Write access to the canonical repository: branches live there, PRs are for review.
    WRITE_ACCESS("Write access, PR on the repository", "write-access", false),
    /// A one-person repository: no PRs, push straight to the default branch.
    ORIGIN_ONLY("Own repository, push to the default branch", "origin-only", false),
    /// A semantic fork: upstream plus a stack of open PRs merged on top.
    EXPERIMENTAL_FORK("Semantic fork (upstream + open PRs)", "experimental-fork", false);

    private final String label;
    private final String resource;
    private final boolean fork;

    RepoType(String label, String resource, boolean fork) {
        this.label = label;
        this.resource = resource;
        this.fork = fork;
    }

    /// Whether the setup session clones a fork of the repository rather than
    /// the repository itself.
    public boolean fork() {
        return fork;
    }

    /// The workspace-root `CLAUDE.md` template for this repository type.
    /// A missing resource is a packaging bug, not a runtime condition.
    public String template() {
        try (InputStream in = RepoType.class.getResourceAsStream("claudemd/" + resource + ".md")) {
            if (in == null) {
                throw new IllegalStateException("Missing CLAUDE.md template " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// What the dialog's combo box shows.
    @Override
    public String toString() {
        return label;
    }
}
