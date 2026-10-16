package com.youneshatti.jarboa.data.xmpp

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** The deadline includes waiting for the connection lock, not just the IQ request.
 * Smack's interruptible IQ waits must release the lock when the caller leaves the chat.
 */
internal suspend fun <T> interruptibleXmppOperation(
    mutex: Mutex,
    timeoutMillis: Long = 20_000,
    block: () -> T,
): T = (withTimeoutOrNull(timeoutMillis) {
    Result.success(mutex.withLock { runInterruptible(Dispatchers.IO, block) })
} ?: throw XmppOperationTimeoutException()).getOrThrow()

internal class XmppOperationTimeoutException : IOException("XMPP operation timed out. Please retry.")
