package com.youneshatti.jarboa.domain.connection

import kotlin.math.min
import kotlin.math.pow

class ReconnectionBackoff(
    private val initialDelaySeconds: Int = 2,
    private val maximumDelaySeconds: Int = 60,
) {
    init {
        require(initialDelaySeconds > 0)
        require(maximumDelaySeconds >= initialDelaySeconds)
    }

    fun delaySeconds(attempt: Int): Int {
        require(attempt >= 0)
        val exponential = initialDelaySeconds.toDouble() * 2.0.pow(attempt.toDouble())
        return min(exponential.toInt(), maximumDelaySeconds)
    }
}

