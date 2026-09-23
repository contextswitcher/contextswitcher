package com.contextswitcher.switching;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskStatus;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~chat-focus-action~2]
class ChatFocusActionTest {

    private static Task task(@Nullable String chat) {
        return new Task("jabref/fix", "T", TaskStatus.ACTIVE, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), null, chat, "");
    }

    /// A resolver over a fixed group→chat map, standing in for reading
    /// `CONTEXTSWITCHER.md`.
    private static ChatFocusAction action(Consumer<String> opener, Map<String, String> groupChats) {
        return new ChatFocusAction(opener, groupChats::get);
    }

    @Test
    void notConfiguredWithoutTaskOrCategoryChat() {
        assertThat(action(url -> { }, Map.of()).isConfigured(task(null))).isFalse();
    }

    @Test
    void configuredFromCategoryChatWhenTaskHasNone() {
        assertThat(action(url -> { }, Map.of("jabref", "https://matrix.to/#/!room:matrix.org"))
                .isConfigured(task(null))).isTrue();
    }

    @Test
    void opensTheCategoryChatAsFallback() {
        AtomicReference<String> opened = new AtomicReference<>();
        ActionResult result = action(opened::set, Map.of("jabref", "https://matrix.to/#/!group:matrix.org"))
                .run(task(null));
        assertThat(result.ok()).isTrue();
        assertThat(result.detail()).isEqualTo("opening category chat");
        assertThat(opened.get()).isEqualTo("element://#/room/!group:matrix.org");
    }

    @Test
    void theTaskChatWinsOverTheCategory() {
        AtomicReference<String> opened = new AtomicReference<>();
        ActionResult result = action(opened::set, Map.of("jabref", "https://matrix.to/#/!group:matrix.org"))
                .run(task("https://matrix.to/#/!own:matrix.org"));
        assertThat(result.detail()).isEqualTo("opening task chat");
        assertThat(opened.get()).isEqualTo("element://#/room/!own:matrix.org");
    }

    @Test
    void roomPermalinkBecomesElementDeepLink() {
        assertThat(ChatFocusAction.deepLink("https://matrix.to/#/!room:matrix.org/$evt?via=matrix.org"))
                .isEqualTo("element://#/room/!room:matrix.org/$evt?via=matrix.org");
    }

    @Test
    void aliasPermalinkIsARoomToo() {
        assertThat(ChatFocusAction.deepLink("https://matrix.to/#/#jabref:matrix.org"))
                .isEqualTo("element://#/room/#jabref:matrix.org");
    }

    @Test
    void userPermalinkTakesTheUserRoute() {
        assertThat(ChatFocusAction.deepLink("https://matrix.to/#/@koppor:matrix.org"))
                .isEqualTo("element://#/user/@koppor:matrix.org");
    }

    @Test
    void otherUrlsArePassedThrough() {
        assertThat(ChatFocusAction.deepLink("element://#/room/!x:y")).isEqualTo("element://#/room/!x:y");
        assertThat(ChatFocusAction.deepLink("https://chat.example.org/room")).isEqualTo("https://chat.example.org/room");
    }

    @Test
    void runOpensTheDeepLink() {
        AtomicReference<String> opened = new AtomicReference<>();
        ActionResult result = action(opened::set, Map.of())
                .run(task("https://matrix.to/#/!room:matrix.org"));
        assertThat(result.ok()).isTrue();
        assertThat(opened.get()).isEqualTo("element://#/room/!room:matrix.org");
    }

    @Test
    void openerFailureIsReported() {
        ActionResult result = action(url -> {
            throw new IllegalStateException("no handler");
        }, Map.of()).run(task("https://matrix.to/#/!room:matrix.org"));
        assertThat(result.ok()).isFalse();
        assertThat(result.detail()).contains("no handler");
    }
}
