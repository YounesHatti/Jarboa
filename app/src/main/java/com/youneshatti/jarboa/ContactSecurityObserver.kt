package com.youneshatti.jarboa

import com.youneshatti.jarboa.domain.model.OmemoContactSecurity
import com.youneshatti.jarboa.domain.model.OmemoContactStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

internal data class ContactSecurityRequest(
    val jid: String,
    val ready: Boolean,
    val refresh: Long = 0,
)

/** One discovery job for the visible chat, cancelled on navigation or reconnect. */
internal suspend fun observeContactSecurity(
    requests: Flow<ContactSecurityRequest?>,
    prepare: suspend (String) -> Unit,
    load: suspend (String) -> OmemoContactSecurity,
    publish: (OmemoContactSecurity?) -> Unit,
) {
    requests.distinctUntilChanged().collectLatest { request ->
        if (request == null) {
            publish(null)
            return@collectLatest
        }
        publish(OmemoContactSecurity.checking(request.jid))
        if (!request.ready) return@collectLatest
        prepare(request.jid)
        var retryDelay = 2_000L
        while (true) {
            val security = try {
                load(request.jid)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                OmemoContactSecurity(
                    jid = request.jid,
                    status = OmemoContactStatus.UNAVAILABLE,
                    detail = "OMEMO device information could not be loaded. Jarboa will retry automatically.",
                )
            }
            // Also guard against a blocking library call returning after cancellation.
            currentCoroutineContext().ensureActive()
            publish(security)
            // Identity changes and rejected devices require a user decision, not retries.
            if (security.status != OmemoContactStatus.UNAVAILABLE) return@collectLatest
            delay(retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(30_000L)
        }
    }
}
