package com.youneshatti.jarboa.domain.connection

import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectionBackoffTest {
    private val backoff = ReconnectionBackoff(initialDelaySeconds = 2, maximumDelaySeconds = 60)

    @Test
    fun `delay grows exponentially`() {
        assertEquals(listOf(2, 4, 8, 16, 32), (0..4).map(backoff::delaySeconds))
    }

    @Test
    fun `delay is capped`() {
        assertEquals(60, backoff.delaySeconds(5))
        assertEquals(60, backoff.delaySeconds(100))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative attempts are rejected`() {
        backoff.delaySeconds(-1)
    }
}
