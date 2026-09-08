package com.youneshatti.jarboa

import com.youneshatti.jarboa.domain.model.OmemoContactSecurity
import com.youneshatti.jarboa.domain.model.OmemoContactStatus
import com.youneshatti.jarboa.domain.model.OmemoDeviceInfo
import com.youneshatti.jarboa.domain.model.OmemoTrustLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContactSecurityObserverTest {
    private val jid = "friend@example.org"
    private fun ready(address: String = jid, trust: OmemoTrustLevel = OmemoTrustLevel.UNVERIFIED) =
        OmemoContactSecurity(address, OmemoContactStatus.READY, listOf(OmemoDeviceInfo(1, "fingerprint", trust = trust)))

    @Test
    fun `unavailable discovery recovers without navigating away`() = runTest {
        val requests = MutableStateFlow<ContactSecurityRequest?>(ContactSecurityRequest(jid, true))
        var calls = 0
        var latest: OmemoContactSecurity? = null
        backgroundScope.launch {
            observeContactSecurity(requests, {}, {
                if (++calls == 1) OmemoContactSecurity(it, OmemoContactStatus.UNAVAILABLE) else ready(it)
            }, { latest = it })
        }
        runCurrent()
        assertFalse(latest!!.canSend)
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(latest!!.canSend)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(2, calls)
    }

    @Test
    fun `waits for OMEMO then refreshes on reconnect`() = runTest {
        val requests = MutableStateFlow<ContactSecurityRequest?>(ContactSecurityRequest(jid, false))
        var calls = 0
        var latest: OmemoContactSecurity? = null
        backgroundScope.launch {
            observeContactSecurity(requests, {}, { calls++; ready(it) }, { latest = it })
        }
        runCurrent()
        assertEquals(0, calls)
        assertFalse(latest!!.canSend)
        requests.value = ContactSecurityRequest(jid, true)
        runCurrent()
        assertTrue(latest!!.canSend)
        requests.value = ContactSecurityRequest(jid, false)
        runCurrent()
        assertFalse(latest!!.canSend)
        requests.value = ContactSecurityRequest(jid, true)
        runCurrent()
        assertEquals(2, calls)
        assertTrue(latest!!.canSend)
    }

    @Test
    fun `backgrounding cancels retries and foregrounding refreshes`() = runTest {
        val requests = MutableStateFlow<ContactSecurityRequest?>(ContactSecurityRequest(jid, true))
        var calls = 0
        var latest: OmemoContactSecurity? = null
        backgroundScope.launch {
            observeContactSecurity(requests, {}, { calls++; throw IllegalStateException("offline") }, { latest = it })
        }
        runCurrent()
        requests.value = null
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, calls)
        assertNull(latest)
        requests.value = ContactSecurityRequest(jid, true)
        runCurrent()
        assertEquals(2, calls)
    }

    @Test
    fun `old chat result cannot overwrite new chat even if loader swallows cancellation`() = runTest {
        val requests = MutableStateFlow<ContactSecurityRequest?>(ContactSecurityRequest(jid, true))
        val results = mutableListOf<OmemoContactSecurity?>()
        backgroundScope.launch {
            observeContactSecurity(requests, {}, {
                if (it == jid) {
                    try { awaitCancellation() } catch (_: CancellationException) { ready(it) }
                } else ready(it)
            }, { results.add(it) })
        }
        runCurrent()
        requests.value = ContactSecurityRequest("second@example.org", true)
        runCurrent()
        assertEquals("second@example.org", results.last()!!.jid)
        assertTrue(results.none { it?.jid == jid && it.canSend })
    }

    @Test
    fun `manual refresh cancels pending backoff and retries immediately`() = runTest {
        val requests = MutableStateFlow<ContactSecurityRequest?>(ContactSecurityRequest(jid, true))
        var calls = 0
        backgroundScope.launch {
            observeContactSecurity(requests, {}, {
                calls++
                OmemoContactSecurity(it, OmemoContactStatus.UNAVAILABLE)
            }, {})
        }
        runCurrent()
        requests.value = ContactSecurityRequest(jid, true, refresh = 1)
        runCurrent()
        assertEquals(2, calls)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(3, calls)
    }

    @Test
    fun `retry backoff is capped and roster preparation is not repeated`() = runTest {
        val requests = MutableStateFlow<ContactSecurityRequest?>(ContactSecurityRequest(jid, true))
        var prepared = 0
        var calls = 0
        backgroundScope.launch {
            observeContactSecurity(requests, { prepared++ }, {
                calls++
                OmemoContactSecurity(it, OmemoContactStatus.UNAVAILABLE)
            }, {})
        }
        runCurrent()
        for (wait in listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L)) {
            val before = calls
            advanceTimeBy(wait - 1)
            runCurrent()
            assertEquals(before, calls)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(before + 1, calls)
        }
        assertEquals(1, prepared)
    }

    @Test
    fun `changed and rejected identities stay blocked without automatic trust changes`() = runTest {
        for (trust in listOf(OmemoTrustLevel.CHANGED, OmemoTrustLevel.REJECTED)) {
            val requests = MutableStateFlow<ContactSecurityRequest?>(ContactSecurityRequest(jid, true))
            var calls = 0
            var latest: OmemoContactSecurity? = null
            val job = backgroundScope.launch {
                observeContactSecurity(requests, {}, { calls++; ready(it, trust) }, { latest = it })
            }
            runCurrent()
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(1, calls)
            assertFalse(latest!!.canSend)
            assertEquals(trust, latest!!.devices.single().trust)
            job.cancel()
        }
    }
}
