package com.contextswitcher.android.watch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// [utest->dsn~android-enqueue~1]
class EnqueuedDeliveryTest {

    @Test
    fun `one message per idle turn`() {
        val delivery = EnqueuedDelivery()
        assertThat(delivery.ready("working")).isFalse()
        assertThat(delivery.ready("attention")).isFalse()
        assertThat(delivery.ready(null)).isFalse()
        assertThat(delivery.ready("waiting")).isTrue()
        delivery.delivered()
        assertThat(delivery.ready("waiting")).`as`("the reading right after the paste").isFalse()
        assertThat(delivery.ready("working")).isFalse()
        assertThat(delivery.ready("done")).isTrue()
    }
}
