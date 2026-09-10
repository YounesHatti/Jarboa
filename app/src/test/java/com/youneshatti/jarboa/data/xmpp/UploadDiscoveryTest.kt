package com.youneshatti.jarboa.data.xmpp

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UploadDiscoveryTest {
    @Test fun unresponsiveServicesDoNotHideAWorkingUploadService() = runTest {
        var cancelled = 0
        val result = findResponsiveUploadService(listOf("slow1", "slow2", "slow3", "upload")) {
            if (it == "upload") true else try { awaitCancellation() } finally { cancelled++ }
        }
        assertEquals("upload", result)
        assertEquals(3, cancelled)
        assertEquals(0, testScheduler.currentTime)
    }

    @Test fun discoveryIsBoundedAndContinuesAfterTimeoutsAndErrors() = runTest {
        var active = 0
        var maximum = 0
        val result = findResponsiveUploadService((0..8).toList()) { candidate ->
            active++
            maximum = maxOf(maximum, active)
            try {
                when (candidate) {
                    in 0..3 -> awaitCancellation()
                    4 -> error("Broken unrelated component")
                    8 -> true
                    else -> false
                }
            } finally { active-- }
        }
        assertEquals(8, result)
        assertEquals(4, maximum)
        assertEquals(0, active)
        assertEquals(5_000, testScheduler.currentTime)
    }

    @Test fun cancellationStopsEveryProbeAndDoesNotLeakAResult() = runTest {
        var active = 0
        val job = async {
            findResponsiveUploadService((0..20).toList()) {
                active++
                try { awaitCancellation() } finally { active-- }
            }
        }
        runCurrent()
        assertEquals(4, active)
        job.cancel()
        runCurrent()
        assertTrue(job.isCancelled)
        assertEquals(0, active)
    }

    @Test fun allUnsupportedReturnsNullWithoutNegativeCaching() = runTest {
        assertNull(findResponsiveUploadService(listOf("service")) { false })
        assertEquals("service", findResponsiveUploadService(listOf("service")) { true })
    }
}
