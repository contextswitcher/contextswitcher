package com.contextswitcher.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import atlantafx.base.theme.Styles;
import com.contextswitcher.local.LocalCommandRunner;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// The personalized "What's new": every `CHANGELOG.md` bullet whose first
/// line `git blame` attributes to a commit in `since..until`, grouped by who
/// wrote it — *changes by* each other author by name, then *changes by me*
/// (`git config user.email`) — each bullet keeping its day and
/// `### Added/Changed/Fixed` heading — a projection of the changelog, not a
/// diff. The changelog is blamed **at** `until`, so `until` may be a fetched
/// upstream commit the working tree does not have yet.
///
/// Shared by `scripts/WhatsNew.java` (news since the previous run, before
/// the app starts) and the running app, whose [#pending] projection needs
/// no commit at all: it diffs any blamed changelog — the working tree, a
/// fetched `@{u}` — against the last announced copy on disk
/// (`dsn~whats-new-upstream~7`).
// [impl->dsn~whats-new-upstream~7]
public final class WhatsNew {

    /// Who wrote a bullet in this checkout: uncommitted, or committed under `user.email`.
    public static final String ME = "me";

    /// Who wrote a bullet only fetched so far: `user.email`, but pushed from elsewhere.
    public static final String ME_REMOTELY = "me — remotely";

    /// One changelog bullet: its first line plus continuation lines, `by` the
    /// author's name, [#ME] or [#ME_REMOTELY].
    public record Item(String by, String day, String heading, List<String> lines) {

        /// The bullet regardless of who wrote it — the identity the pending
        /// projection deduplicates and diffs by.
        Item key() {
            return new Item("", day, heading, lines);
        }
    }

    /// A changelog as `git blame` sees it: its lines and, per line, the commit
    /// and who wrote it.
    public record Source(List<String> lines, List<String> commits, List<String> by) {
    }

    private static final Pattern BLAME_HEADER = Pattern.compile("[0-9a-f]{40} \\d+ \\d+.*");

    private WhatsNew() {
    }

    /// The bullets new in `since..until`, or an empty list when git cannot
    /// answer (no commits, no changelog at `until`) — the news is an offer,
    /// never an error.
    public static List<Item> collect(Path repo, String since, String until) {
        return collect(repo, since, until, new LocalCommandRunner()::run);
    }

    /// As [#collect(Path, String, String)], with the git runner injected for tests.
    static List<Item> collect(Path repo, String since, String until,
            Function<List<String>, LocalCommandRunner.LocalResult> git) {
        List<String> fresh = lines(git, repo, "rev-list", since + ".." + until);
        if (fresh == null || fresh.isEmpty()) {
            return List.of();
        }
        Source source = blame(repo, until, ME, git);
        return source == null ? List.of() : items(source, new HashSet<>(fresh));
    }

    /// The pure projection: the bullets of `source` whose first line a commit
    /// of `fresh` (full shas) wrote.
    static List<Item> items(Source source, Set<String> fresh) {
        return bullets(source.lines(), n -> fresh.contains(source.commits().get(n)) ? source.by().get(n) : null);
    }

    /// `CHANGELOG.md` blamed at `rev`, the working tree when null; a line of
    /// `user.email` or not committed yet is `me`. Null when git cannot say.
    public static @Nullable Source blame(Path repo, @Nullable String rev, String me) {
        return blame(repo, rev, me, new LocalCommandRunner()::run);
    }

    private static @Nullable Source blame(Path repo, @Nullable String rev, String me,
            Function<List<String>, LocalCommandRunner.LocalResult> git) {
        List<String> porcelain = rev == null
                ? lines(git, repo, "blame", "--line-porcelain", "--", "CHANGELOG.md")
                : lines(git, repo, "blame", "--line-porcelain", rev, "--", "CHANGELOG.md");
        if (porcelain == null) {
            return null;
        }
        List<String> mail = lines(git, repo, "config", "user.email");
        return parse(porcelain, mail == null || mail.isEmpty() ? "" : mail.getFirst().strip(), me);
    }

    /// `git blame --line-porcelain` output as a [Source]: the header starts a
    /// line, `author`/`author-mail` say who, the tab-prefixed line is the text.
    static Source parse(List<String> porcelain, String mail, String me) {
        List<String> lines = new ArrayList<>();
        List<String> commits = new ArrayList<>();
        List<String> by = new ArrayList<>();
        String commit = "";
        String name = "";
        String who = "";
        for (String l : porcelain) {
            if (l.startsWith("\t")) {
                lines.add(l.substring(1));
                commits.add(commit);
                by.add(who);
            } else if (BLAME_HEADER.matcher(l).matches()) {
                commit = l.substring(0, 40);
            } else if (l.startsWith("author ")) {
                name = l.substring("author ".length());
            } else if (l.startsWith("author-mail ")) {
                String m = l.substring("author-mail <".length(), l.length() - 1);
                who = m.equalsIgnoreCase(mail) || commit.chars().allMatch(c -> c == '0') ? me : name;
            }
        }
        return new Source(lines, commits, by);
    }

    /// Every bullet of `changelog` for which `byAt` (given the line index of
    /// the bullet's first line) says who wrote it — null drops it.
    private static List<Item> bullets(List<String> changelog, IntFunction<@Nullable String> byAt) {
        List<Item> items = new ArrayList<>();
        String day = "";
        String heading = "";
        Item current = null;
        for (int n = 0; n < changelog.size(); n++) {
            String l = changelog.get(n);
            if (l.startsWith("## ")) {
                day = l.substring(3).replaceAll("^\\[(.*?)\\].*", "$1");
                current = null;
            } else if (l.startsWith("### ")) {
                heading = l.substring(4);
                current = null;
            } else if (l.startsWith("- ")) {
                current = null;
                String by = byAt.apply(n);
                if (by != null) {
                    current = new Item(by, day, heading, new ArrayList<>(List.of(l.substring(2))));
                    items.add(current);
                }
            } else if (current != null && l.startsWith("  ")) {
                current.lines().add(l.strip()); // continuation line of a bullet
            } else {
                current = null;
            }
        }
        return items;
    }

    /// The bullets of `sources` (the local working tree under `true`, a
    /// fetched upstream under `false`) that the last announced `copy` does not
    /// hold — set-wise, so a bullet in two sources counts once and a bullet is
    /// never news twice. No copy yet (first run) means everything is old: the
    /// app must not greet a fresh install with the whole changelog.
    public static List<Item> pending(Path copy, Map<Boolean, Source> sources) {
        List<String> announced = read(copy);
        if (announced == null) {
            return List.of();
        }
        Set<Item> old = new HashSet<>();
        bullets(announced, n -> "").forEach(it -> old.add(it.key()));
        Map<Item, Item> fresh = new LinkedHashMap<>();
        for (boolean local : new boolean[] {true, false}) { // local first: a pulled bullet is not "remotely"
            Source source = sources.get(local);
            if (source != null) {
                bullets(source.lines(), source.by()::get).forEach(it -> fresh.putIfAbsent(it.key(), it));
            }
        }
        return fresh.entrySet().stream().filter(e -> !old.contains(e.getKey())).map(Map.Entry::getValue).toList();
    }

    /// Replaces the announced copy with every source seen: their bullets are
    /// old from now on, whichever trigger sees them next. Written on the
    /// first run too, so the copy exists to diff against.
    public static void announce(Path copy, Map<Boolean, Source> sources) throws IOException {
        Files.createDirectories(copy.getParent());
        List<String> all = new ArrayList<>();
        sources.values().forEach(s -> all.addAll(s.lines()));
        Files.write(copy, all);
    }

    private static @Nullable List<String> read(Path file) {
        try {
            return Files.exists(file) ? Files.readAllLines(file) : null;
        } catch (IOException e) {
            Logger.warn("Cannot read {}: {}", file, e.toString());
            return null;
        }
    }

    private static @Nullable List<String> lines(Function<List<String>, LocalCommandRunner.LocalResult> git,
            Path repo, String... args) {
        List<String> cmd = new ArrayList<>(List.of("git", "-C", repo.toString()));
        cmd.addAll(List.of(args));
        LocalCommandRunner.LocalResult result = git.apply(cmd);
        if (!result.ok()) {
            Logger.debug("git {} failed: {}", String.join(" ", args), result.stderr().strip());
            return null;
        }
        return result.stdout().lines().toList();
    }

    /// `by` cut to at most 12 characters (the last one an ellipsis), so a long
    /// author name does not crowd the bullet it prefixes in the update tooltip.
    public static String shortBy(String by) {
        return by.length() <= 12 ? by : by.substring(0, 11).strip() + "\u2026";
    }

    /// Who wrote what, in display order: every other author by first
    /// appearance, then [#ME_REMOTELY], then [#ME].
    private static List<String> groups(List<Item> items) {
        List<String> groups = new ArrayList<>(items.stream().map(Item::by)
                .filter(by -> !by.equals(ME) && !by.equals(ME_REMOTELY)).distinct().toList());
        for (String me : List.of(ME_REMOTELY, ME)) {
            if (items.stream().anyMatch(it -> it.by().equals(me))) {
                groups.add(me);
            }
        }
        return groups;
    }

    /// The scrollable window body: the two groups with their day and heading
    /// labels, `**bold**`, `` `code` `` and bare URLs rendered (the latter
    /// clickable through `openUrl`).
    public static ScrollPane view(List<Item> items, Consumer<String> openUrl) {
        VBox box = new VBox(6);
        box.setPadding(new Insets(16));
        for (String group : groups(items)) {
            String lastDay = "";
            String lastHeading = "";
            boolean first = true;
            for (Item it : items) {
                if (!it.by().equals(group)) {
                    continue;
                }
                if (first) {
                    box.getChildren().add(title("Changes by " + group, Styles.TITLE_2, 12));
                    first = false;
                }
                if (!it.day().equals(lastDay)) {
                    box.getChildren().add(title(it.day(), Styles.TITLE_4, 10));
                    lastDay = it.day();
                    lastHeading = "";
                }
                if (!it.heading().equals(lastHeading)) {
                    box.getChildren().add(title(it.heading(), Styles.TEXT_MUTED, 4));
                    lastHeading = it.heading();
                }
                VBox bullet = new VBox(2);
                bullet.setPadding(new Insets(0, 0, 4, 16));
                for (String l : it.lines()) {
                    bullet.getChildren().add(inline(l, openUrl));
                }
                box.getChildren().add(bullet);
            }
        }
        ScrollPane scroll = new ScrollPane(box);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private static Label title(String text, String style, double topGap) {
        Label l = new Label(text);
        l.getStyleClass().add(style);
        l.setPadding(new Insets(topGap, 0, 0, 0));
        return l;
    }

    private static final Pattern INLINE =
            Pattern.compile("\\*\\*(.+?)\\*\\*|`([^`]+)`|(https?://\\S+?)(?=[\\s)\\]]|$)");

    private static TextFlow inline(String line, Consumer<String> openUrl) {
        TextFlow flow = new TextFlow();
        Matcher m = INLINE.matcher(line);
        int pos = 0;
        while (m.find()) {
            flow.getChildren().add(new Text(line.substring(pos, m.start())));
            if (m.group(1) != null) {
                for (Node child : List.copyOf(inline(m.group(1), openUrl).getChildren())) { // bold may hold code and links
                    child.getStyleClass().add(Styles.TEXT_BOLD);
                    flow.getChildren().add(child);
                }
            } else if (m.group(2) != null) {
                Text t = new Text(m.group(2));
                t.getStyleClass().add("monospace");
                flow.getChildren().add(t);
            } else {
                String url = m.group(3);
                Hyperlink h = new Hyperlink(url);
                h.setPadding(Insets.EMPTY);
                h.setOnAction(e -> openUrl.accept(url));
                flow.getChildren().add(h);
            }
            pos = m.end();
        }
        flow.getChildren().add(new Text(line.substring(pos)));
        return flow;
    }

    /// The same grouping as [#view] as text, for a terminal.
    public static String plainText(List<Item> items) {
        StringBuilder sb = new StringBuilder();
        for (String group : groups(items)) {
            String lastDay = "";
            String lastHeading = "";
            boolean first = true;
            for (Item it : items) {
                if (!it.by().equals(group)) {
                    continue;
                }
                if (first) {
                    sb.append(sb.isEmpty() ? "" : "\n").append("═══ ")
                            .append("Changes by ").append(group).append(" ═══\n");
                    first = false;
                }
                if (!it.day().equals(lastDay)) {
                    sb.append("\n").append(it.day()).append("\n");
                    lastDay = it.day();
                    lastHeading = "";
                }
                if (!it.heading().equals(lastHeading)) {
                    sb.append("  ").append(it.heading()).append("\n");
                    lastHeading = it.heading();
                }
                sb.append("    - ").append(String.join("\n      ", it.lines()).replace("**", "")).append("\n");
            }
        }
        return sb.toString();
    }
}
