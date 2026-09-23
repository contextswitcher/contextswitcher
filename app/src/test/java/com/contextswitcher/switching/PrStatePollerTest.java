package com.contextswitcher.switching;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.contextswitcher.local.LocalCommandRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~pr-poll-economy~2]
class PrStatePollerTest {

    /// Records each batch instead of running gh.
    static class RecordingLookup extends PrStateLookup {
        final List<Set<String>> batches = java.util.Collections.synchronizedList(new ArrayList<>());
        final Map<String, PrInfo> answers = new HashMap<>();

        RecordingLookup() {
            super(new LocalCommandRunner());
        }

        @Override
        public Map<String, PrInfo> states(Collection<String> urls) {
            batches.add(new HashSet<>(urls));
            Map<String, PrInfo> result = new HashMap<>(answers);
            result.keySet().retainAll(urls);
            return result;
        }
    }

    private static void tick(PrStatePoller poller) throws Exception {
        var method = PrStatePoller.class.getDeclaredMethod("tickPoll");
        method.setAccessible(true);
        method.invoke(poller);
    }

    @Test
    void mergedPrsAreOnlyPolledOnStartupAndEveryTenMinutes() throws Exception {
        RecordingLookup lookup = new RecordingLookup();
        lookup.answers.put("m", new PrInfo(PrState.MERGED, "t", Map.of()));
        lookup.answers.put("o", new PrInfo(PrState.OPEN, "t", Map.of()));
        // 120 s interval -> merged due every 5th tick (600 s).
        try (PrStatePoller poller = new PrStatePoller(lookup, () -> Set.of("m", "o"),
                states -> { }, 120)) {
            tick(poller);   // startup: everything, merged included
            assertThat(lookup.batches).containsExactly(Set.of("m", "o"));
            for (int i = 0; i < 4; i++) {
                tick(poller);   // known-merged sits out the regular ticks
            }
            assertThat(lookup.batches.subList(1, 5)).allSatisfy(
                    batch -> assertThat(batch).containsExactly("o"));
            tick(poller);   // 600 s reached: merged re-read once
            assertThat(lookup.batches.get(5)).containsExactlyInAnyOrder("m", "o");
        }
    }

    @Test
    void refreshMissingCoalescesTheBurstAndSkipsResolvedUrls() throws Exception {
        RecordingLookup lookup = new RecordingLookup();
        lookup.answers.put("a", new PrInfo(PrState.OPEN, "t", Map.of()));
        Set<String> visible = java.util.Collections.synchronizedSet(new HashSet<>());
        try (PrStatePoller poller = new PrStatePoller(lookup, () -> visible, states -> { }, 120)) {
            tick(poller);   // nothing visible yet
            assertThat(lookup.batches).isEmpty();
            // A burst of row changes: one round, over the final URL set.
            visible.add("a");
            poller.refreshMissing();
            visible.add("b");
            poller.refreshMissing();
            awaitBatches(lookup, 1);
            assertThat(lookup.batches).containsExactly(Set.of("a", "b"));
            // "b" stayed unresolved -> retried, "a" is cached and sits out.
            awaitBatches(lookup, 2);
            assertThat(lookup.batches.get(1)).containsExactly("b");
        }
    }

    private static void awaitBatches(RecordingLookup lookup, int count) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        while (lookup.batches.size() < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(lookup.batches).hasSizeGreaterThanOrEqualTo(count);
    }
}
