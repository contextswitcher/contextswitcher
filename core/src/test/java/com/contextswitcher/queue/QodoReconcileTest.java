package com.contextswitcher.queue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~qodo-agent-prompt-queue~11]
class QodoReconcileTest {

    @Test
    void addOnlyAppendsNewPromptsAndRecordsThem() {
        // The poller path: removeResolved = false — never drops anything.
        QodoReconcile.Result result = QodoReconcile.apply(
                List.of(), Set.of(), List.of("A", "B"), false);
        assertThat(result.queue()).containsExactly("A", "B");
        assertThat(result.qodoSourced()).containsExactlyInAnyOrder("A", "B");
        assertThat(result.added()).isEqualTo(2);
        assertThat(result.removed()).isZero();
    }

    @Test
    void addOnlyIsIdempotentForAnAlreadyQueuedPrompt() {
        QodoReconcile.Result result = QodoReconcile.apply(
                List.of("A"), Set.of("A"), List.of("A"), false);
        assertThat(result.queue()).containsExactly("A");
        assertThat(result.added()).isZero();
        assertThat(result.removed()).isZero();
    }

    @Test
    void syncDropsResolvedQodoPromptsAndAddsNewOnes() {
        // Queue had qodo's A and B; a push resolved B and raised C.
        QodoReconcile.Result result = QodoReconcile.apply(
                List.of("A", "B"), Set.of("A", "B"), List.of("A", "C"), true);
        assertThat(result.queue()).containsExactly("A", "C");
        assertThat(result.qodoSourced()).containsExactlyInAnyOrder("A", "C");
        assertThat(result.added()).isEqualTo(1);
        assertThat(result.removed()).isEqualTo(1);
    }

    @Test
    void syncNeverTouchesHandTypedMessages() {
        // "mine" was never sourced from qodo — it must survive even a full sync
        // that resolves every qodo prompt.
        QodoReconcile.Result result = QodoReconcile.apply(
                List.of("A", "mine"), Set.of("A"), List.of(), true);
        assertThat(result.queue()).containsExactly("mine");
        assertThat(result.removed()).isEqualTo(1);
    }

    @Test
    void syncDoesNotClobberAnEditedQodoPrompt() {
        // The user edited qodo's "A" into "A + note"; it is no longer verbatim,
        // so it is treated as their own and kept.
        QodoReconcile.Result result = QodoReconcile.apply(
                List.of("A + note"), Set.of("A"), List.of(), true);
        assertThat(result.queue()).containsExactly("A + note");
        assertThat(result.removed()).isZero();
    }

    @Test
    void syncDoesNotReAddAPromptTheUserDeleted() {
        // A is still suggested but the user removed it from the queue; a sync
        // must respect that and not resurrect it.
        QodoReconcile.Result result = QodoReconcile.apply(
                List.of(), Set.of("A"), List.of("A"), true);
        assertThat(result.queue()).isEmpty();
        assertThat(result.added()).isZero();
        assertThat(result.removed()).isZero();
        // Still remembered (so it stays not-re-added), because it is still active.
        assertThat(result.qodoSourced()).containsExactly("A");
    }

    @Test
    void syncForgetsAPromptThatIsNeitherQueuedNorSuggested() {
        // A was sourced, user deleted it, and now it is also gone from the
        // review — drop it from the memory so a later re-raise can re-add it.
        QodoReconcile.Result result = QodoReconcile.apply(
                List.of(), Set.of("A"), List.of(), true);
        assertThat(result.qodoSourced()).isEmpty();
    }
}
