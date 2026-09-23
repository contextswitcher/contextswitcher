package com.contextswitcher.switching;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.contextswitcher.local.LocalCommandRunner;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// A PR's open review comments — Qodo Merge's per-suggestion "Agent Prompt"
/// blocks **and** what human reviewers wrote — via the locally installed (and
/// authenticated) `gh` CLI, the system-tool approach of MADR 0003, like
/// [PrStateLookup]. Qodo posts each code suggestion as an inline review comment
/// whose body carries a
/// `<details><summary><strong>Agent Prompt</strong></summary>` section with a
/// fenced prompt ready to hand to an AI agent — and repeats it, blockquoted,
/// in its summary comment; a human reviewer just writes prose. ContextSwitcher harvests both into the task's message queue, a human
/// comment prefixed with who wrote it and where ([#reviewMessage]) so it stands
/// on its own in the chat.
///
/// Comments by **the user themselves** are skipped — the `viewer` of the `gh`
/// login, falling back to the PR's author — as are other bots'
/// (`__typename == "Bot"`). In every thread, qodo's as much as a human's,
/// everything up to the user's **last own reply** is skipped: they have seen
/// those comments and answered them, so re-queuing would ask them to deal with
/// the same thing twice. What a reviewer wrote *after* that reply is new and is
/// queued — the follow-up that keeps a discussion going would otherwise be lost
/// for good.
///
/// Comments on a **closed or merged** PR are dropped wholesale: the PR is done
/// with, so its threads — however active they still look — are no work to do.
///
/// The comments are read through the **review threads** GraphQL query rather
/// than the REST review-comments endpoint: only the thread carries
/// `isResolved`, so without it a suggestion someone resolved by hand — without
/// touching the code, so it never goes outdated — keeps looking active and is
/// queued again on every sync.
///
/// The PR's **conversation** — the top-level comments — is read by the same
/// query and harvested the same way. Reviewers write there as much as in the
/// diff, and qodo repeats its findings there in a "Code Review by Qodo" summary
/// comment, sometimes carrying one the inline round left out. Neither has a
/// thread to resolve or a diff position to go outdated, so a top-level comment
/// stands until the user replies below it. A finding that arrives twice is
/// queued once ([#activePrompts], [#promptKey]).
// [impl->dsn~qodo-agent-prompt-queue~11]
public class QodoReviewLookup {

    /// GitHub's login for the free-for-open-source Qodo Merge bot. REST spells
    /// it with a `[bot]` suffix, GraphQL without — [#isQodo] accepts both.
    public static final String BOT = "qodo-free-for-open-source-projects";

    /// The review threads of a PR with their resolution state, plus the PR's
    /// own `state` so a closed one contributes nothing, one page of 100.
    /// Everything the filter needs comes from this one query — `isResolved` (a
    /// human ticked "Resolve conversation") and `isOutdated` (the comment's
    /// position no longer points at the diff) are both thread-level and neither
    /// is exposed by the REST review-comments endpoint. Written with GraphQL
    /// *variables*, so the argv carries no double quote — the Windows trap of
    /// `dsn~pr-state-indicator~3`.
    private static final String THREADS_QUERY = """
            query($owner:String!,$repo:String!,$number:Int!,$endCursor:String){
              viewer{ login }
              repository(owner:$owner,name:$repo){
                pullRequest(number:$number){
                  state
                  author{ login }
                  comments(first:100){ nodes{ url body author{ login __typename } } }
                  reviewThreads(first:100,after:$endCursor){
                    pageInfo{ hasNextPage endCursor }
                    nodes{
                      isResolved
                      isOutdated
                      path
                      line
                      comments(first:20){ nodes{ url body author{ login __typename } } }
                    }
                  }
                }
              }
            }
            """;

    /// The fenced prompt inside an "Agent Prompt" `<details>`: non-greedy so
    /// each `<details>` yields exactly its own block, DOTALL so the prompt's
    /// own newlines are captured. The opening fence may carry a language hint.
    private static final Pattern AGENT_PROMPT = Pattern.compile(
            "<summary>\\s*(?:<strong>\\s*)?Agent [Pp]rompt\\s*(?:</strong>\\s*)?</summary>"
                    + ".*?```[^\\n]*\\n(.*?)\\n```",
            Pattern.DOTALL);

    /// The blockquote marker qodo's summary comment wraps every finding in
    /// (`> <details>`, `>```", `>## Issue description`) — stripped before the
    /// prompt is cut out, so one pattern serves both the inline suggestion and
    /// the summary's copy of it.
    private static final Pattern QUOTE = Pattern.compile("(?m)^>[ \\t]?");

    /// The sentence qodo puts above the prompt in the **summary** comment and
    /// leaves off the inline one. Dropped so the same finding has the same
    /// text from either source and [#activePrompts] can collapse the two.
    /// Qodo has since dropped the `## ` from its headings, and every edit of its
    /// summary stacks one more copy of the sentence on a carried-over finding —
    /// so the anchor takes the heading with or without `## ` and everything
    /// above it goes, however many lines that is.
    private static final Pattern PROMPT_PREAMBLE =
            Pattern.compile("\\A.*?(?=^(?:## )?Issue description$)", Pattern.DOTALL | Pattern.MULTILINE);

    private static final Pattern UPDATED_AT = Pattern.compile("\"updatedAt\"\\s*:\\s*\"([^\"]+)\"");

    private final LocalCommandRunner runner;
    private final ObjectMapper mapper = new ObjectMapper();

    public QodoReviewLookup(LocalCommandRunner runner) {
        this.runner = runner;
    }

    /// One harvested review comment: its URL (the anchored
    /// `…/pull/<n>#discussion_r<id>` link the queue's open-in-browser button
    /// uses), whether it is still `active` — its thread is neither **resolved**
    /// (a human ticked "Resolve conversation") nor **outdated** (the suggestion
    /// was addressed, so the comment no longer points at the diff) — and the
    /// message(s) it contributes: qodo's agent prompt(s) parsed from its body
    /// (usually one), or the single formatted text of a human reviewer's comment.
    public record QodoComment(String url, boolean active, List<String> prompts) {
    }

    static List<String> command(String owner, String repo, String number) {
        return List.of("gh", "api", "graphql", "--paginate",
                "-F", "owner=" + owner,
                "-F", "repo=" + repo,
                "-F", "number=" + number,
                "-f", "query=" + THREADS_QUERY);
    }

    /// True for both spellings of the bot's login, REST's `…[bot]` and
    /// GraphQL's bare one.
    static boolean isQodo(String login) {
        return BOT.equals(login) || (BOT + "[bot]").equals(login);
    }

    /// A human reviewer's comment as a self-contained chat message: who wrote
    /// it, which file/line it hangs on and its permalink, then the comment
    /// text. Without the prefix a bare "use a switch here" reaches the chat
    /// with no anchor at all — qodo's agent prompts carry their own context,
    /// prose does not. The link lets the agent re-read the thread's current
    /// state instead of working from the snapshot in the message.
    static String reviewMessage(String login, String path, int line, String url, String body) {
        String where = path.isEmpty() ? "" : " on " + path + (line > 0 ? ":" + line : "");
        String link = url.isEmpty() ? "" : " (" + url + ")";
        return "Review comment by @" + login + where + link + ":\n\n" + body.strip();
    }

    static List<String> activityCommand(String url) {
        return List.of("gh", "pr", "view", url, "--json", "updatedAt");
    }

    static @Nullable String parseUpdatedAt(String json) {
        Matcher matcher = UPDATED_AT.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    /// The PR's `updatedAt` timestamp — the cheap "did anything happen?" signal
    /// that gates the heavier comment fetch. A push moves it and so does a new
    /// review comment, which the head SHA this used to read does not: a
    /// reviewer writing on a branch nobody pushes to left the head where it
    /// was, and their comment never reached the queue. Null when `url` is no PR
    /// URL, or `gh` is missing/fails/unparseable (all logged, not thrown).
    public @Nullable String lastActivity(String url) {
        if (!PrTitleLookup.PR_URL.matcher(url).matches()) {
            return null;
        }
        try {
            LocalCommandRunner.LocalResult result = runner.run(activityCommand(url));
            if (result.exitCode() == 0 && !result.stdout().isBlank()) {
                return parseUpdatedAt(result.stdout());
            }
            Logger.debug("gh pr view --json updatedAt failed for {} (exit {})", url, result.exitCode());
        } catch (RuntimeException e) {
            Logger.debug("gh unavailable for {}: {}", url, e.getMessage());
        }
        return null;
    }

    /// The agent prompt(s) inside one qodo comment body — empty when it carries
    /// none. Serves both places qodo puts them: the inline suggestion, and the
    /// "Code Review by Qodo" summary comment, where every finding is wrapped in
    /// a Markdown blockquote and the prompt carries a preamble sentence. Both
    /// are normalised away, so the same finding yields the same text either way.
    static List<String> extractPrompts(String body) {
        List<String> prompts = new ArrayList<>();
        Matcher matcher = AGENT_PROMPT.matcher(QUOTE.matcher(body).replaceAll(""));
        while (matcher.find()) {
            String prompt = PROMPT_PREAMBLE.matcher(matcher.group(1)).replaceFirst("").strip();
            if (!prompt.isEmpty()) {
                prompts.add(prompt);
            }
        }
        return prompts;
    }

    /// A prompt's identity for deduplication: its text with every whitespace run
    /// collapsed. The inline suggestion and the summary's copy of the same
    /// finding differ only in blank lines, so comparing the texts verbatim would
    /// queue both.
    static String promptKey(String prompt) {
        return prompt.replaceAll("\\s+", " ").strip();
    }

    /// Every qodo review comment on `url`, each with its parsed agent
    /// prompt(s). Null (not empty) when `url` is no PR URL or `gh` is
    /// missing/fails/answers unreadably — so callers can tell "GitHub
    /// unreachable" from "qodo left no comments" and never treat a failed fetch
    /// as "everything is resolved" (all failures logged, not thrown).
    public @Nullable List<QodoComment> comments(String url) {
        Matcher pr = PrTitleLookup.PR_URL.matcher(url);
        if (!pr.matches()) {
            return null;
        }
        try {
            LocalCommandRunner.LocalResult result =
                    runner.run(command(pr.group(1), pr.group(2), pr.group(3)));
            if (result.exitCode() == 0) {
                return parse(result.stdout());
            }
            Logger.debug("gh api graphql reviewThreads failed for {} (exit {})", url, result.exitCode());
        } catch (RuntimeException e) {
            Logger.debug("gh unavailable for {}: {}", url, e.getMessage());
        }
        return null;
    }

    /// The agent prompts of the PR's **active** qodo suggestions, in review
    /// order, each mapped to its review comment's URL — the current set to
    /// reconcile the queue against. Null when the fetch failed (see
    /// [#comments]); a successful fetch with no active suggestion is an empty
    /// map.
    public @Nullable Map<String, String> promptsFor(String url) {
        List<QodoComment> comments = comments(url);
        return comments == null ? null : activePrompts(comments);
    }

    /// Flattens the prompts of the active comments, in order, each mapped to
    /// its comment's URL — resolved and outdated suggestions (`active ==
    /// false`) are dropped. The first comment carrying a prompt text wins, so
    /// a duplicated suggestion links to where it was first raised.
    static Map<String, String> activePrompts(List<QodoComment> comments) {
        Map<String, String> prompts = new LinkedHashMap<>();
        Set<String> keys = new LinkedHashSet<>();
        for (QodoComment comment : comments) {
            if (comment.active()) {
                for (String prompt : comment.prompts()) {
                    if (keys.add(promptKey(prompt))) {
                        prompts.put(prompt, comment.url());
                    }
                }
            }
        }
        return prompts;
    }

    /// Index of the **last** comment `me` wrote in this thread, or `-1` when
    /// they wrote none — everything at or before it was seen and answered and
    /// must not come back into the queue, everything after it is a reviewer's
    /// follow-up the user has not dealt with yet. An empty `me` (no `viewer`
    /// and no PR author in the response) matches nobody.
    static int lastReplyBy(String me, JsonNode thread) {
        int last = -1;
        if (me.isEmpty()) {
            return last;
        }
        int index = 0;
        for (JsonNode comment : thread.at("/comments/nodes")) {
            if (me.equals(comment.path("author").path("login").asText())) {
                last = index;
            }
            index++;
        }
        return last;
    }

    /// Every harvested comment of the response, or **null** when the JSON does
    /// not parse. Null matters: an unreadable response means "we do not know
    /// what the PR carries", and returning an empty list instead would tell the
    /// sync every suggestion is resolved and silently leave the queue at
    /// "+0 added" — the failure looking exactly like a clean PR.
    @Nullable List<QodoComment> parse(String json) {
        List<QodoComment> comments = new ArrayList<>();
        if (json.isBlank()) {
            return comments;
        }
        Set<String> seen = new LinkedHashSet<>();
        // Every finding qodo raised inline, answered or not: its summary
        // comment repeats them all, and queuing that copy would resurrect the
        // ones whose thread the user has already dealt with.
        Set<String> inline = new LinkedHashSet<>();
        // The conversation is not the connection `--paginate` pages, so every
        // page repeats it. Taken from the first page and harvested once, after
        // the threads of every page have said which findings are inline.
        JsonNode conversation = null;
        String conversationMe = "";
        try (JsonParser parser = mapper.getFactory().createParser(json)) {
            // `gh api graphql --paginate` prints one response document per page,
            // concatenated — read them all. A document of another shape yields
            // a missing node and simply contributes nothing.
            while (!parser.isClosed()) {
                JsonNode node = mapper.readTree(parser);
                if (node == null) {
                    break;
                }
                String me = node.at("/data/viewer/login").asText("");
                JsonNode pr = node.at("/data/repository/pullRequest");
                if (me.isEmpty()) {
                    me = pr.path("author").path("login").asText("");
                }
                // A closed or merged PR is done with: its threads may still
                // read as active, but nothing on it is work to do any more.
                // Contributing no comments makes the sync's remove half drop
                // whatever it had queued from that PR.
                if (!"OPEN".equals(pr.path("state").asText("OPEN"))) {
                    continue;
                }
                if (conversation == null) {
                    conversation = pr;
                    conversationMe = me;
                }
                for (JsonNode thread : pr.at("/reviewThreads/nodes")) {
                    for (JsonNode comment : thread.at("/comments/nodes")) {
                        if (isQodo(comment.path("author").path("login").asText())) {
                            extractPrompts(comment.path("body").asText(""))
                                    .forEach(prompt -> inline.add(promptKey(prompt)));
                        }
                    }
                    boolean active = !thread.path("isResolved").asBoolean()
                            && !thread.path("isOutdated").asBoolean();
                    collect(comments, seen, thread, me, active,
                            thread.path("path").asText(""), thread.path("line").asInt(0), Set.of());
                }
            }
        } catch (IOException e) {
            Logger.debug("Cannot parse gh review-comment JSON: {}", e.getMessage());
            return null;
        }
        if (conversation != null) {
            // Always active: a top-level comment has no thread to resolve and no
            // diff position to go outdated, so it stands until the user deals
            // with it or writes below it.
            collect(comments, seen, conversation, conversationMe, true, "", 0, inline);
        }
        return comments;
    }

    /// Appends what the comments of one `container` — a review thread or the PR
    /// conversation itself — contribute, skipping everything at or before the
    /// user's last own reply in it, and any qodo prompt whose key is in
    /// `alreadyInline`.
    private void collect(List<QodoComment> out, Set<String> seen, JsonNode container, String me,
            boolean active, String path, int line, Set<String> alreadyInline) {
        int answeredUpTo = lastReplyBy(me, container);
        int index = -1;
        for (JsonNode comment : container.at("/comments/nodes")) {
            index++;
            String login = comment.path("author").path("login").asText();
            String body = comment.path("body").asText("");
            String url = comment.path("url").asText("");
            if (index <= answeredUpTo || !seen.add(url)) {
                continue;   // the user replied below it, or a page already had it
            }
            List<String> messages;
            if (isQodo(login)) {
                messages = extractPrompts(body).stream()
                        .filter(prompt -> !alreadyInline.contains(promptKey(prompt)))
                        .toList();
            } else if (login.isEmpty() || login.equals(me)
                    || "Bot".equals(comment.path("author").path("__typename").asText())
                    || body.isBlank()) {
                continue;   // own reply, another bot, or nothing said
            } else {
                messages = List.of(reviewMessage(login, path, line, url, body));
            }
            out.add(new QodoComment(url, active, messages));
        }
    }
}
