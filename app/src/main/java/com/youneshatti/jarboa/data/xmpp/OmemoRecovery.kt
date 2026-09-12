package com.youneshatti.jarboa.data.xmpp

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** One session-owned worker; a failed attempt must not depend on a chat being reopened. */
internal suspend fun recoverOmemoSession(attempt: suspend () -> Boolean) {
    var retryDelay = 2_000L
    while (true) {
        currentCoroutineContext().ensureActive()
        if (attempt()) return
        delay(retryDelay)
        retryDelay = (retryDelay * 2).coerceAtMost(30_000L)
    }
}
