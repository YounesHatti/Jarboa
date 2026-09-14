package com.youneshatti.jarboa.data.xmpp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/** A non-upload or unresponsive component must not prevent discovery of a working one.
 * At most four requests run together; cancelled workers are joined before returning.
 */
internal suspend fun <T : Any> findResponsiveUploadService(
    candidates: List<T>,
    onFailure: (Throwable) -> Unit = {},
    probe: suspend (T) -> Boolean,
): T? = coroutineScope {
    val found = CompletableDeferred<T?>()
    val permits = Semaphore(4)
    val workers = candidates.distinct().take(64).map { candidate ->
        launch {
            permits.withPermit {
                val supported = try {
                    val result = withTimeoutOrNull(5_000) { probe(candidate) }
                    if (result == null) onFailure(java.util.concurrent.TimeoutException())
                    result == true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    onFailure(error)
                    false
                }
                if (supported) found.complete(candidate)
            }
        }
    }
    val completion = launch { workers.joinAll(); found.complete(null) }
    try { found.await() }
    finally {
        workers.forEach { it.cancel() }
        workers.joinAll()
        completion.cancelAndJoin()
    }
}
