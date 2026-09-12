package com.youneshatti.jarboa.data.xmpp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OmemoRecoveryTest {
    @Test
    fun connectedSessionRecoversWithoutAnyChatOrForegroundEvent() = runTest {
        val attempts = mutableListOf<Long>()
        recoverOmemoSession {
            attempts += testScheduler.currentTime
            attempts.size == 3
        }
        assertEquals(listOf(0L, 2_000L, 6_000L), attempts)
    }

    @Test
    fun retriesBackOffAndStopImmediatelyAfterSuccess() = runTest {
        val attempts = mutableListOf<Long>()
        launch { recoverOmemoSession { attempts += testScheduler.currentTime; attempts.size == 8 } }
        advanceUntilIdle()
        assertEquals(listOf(0L, 2_000L, 6_000L, 14_000L, 30_000L, 60_000L, 90_000L, 120_000L), attempts)
        advanceTimeBy(120_000)
        assertEquals(8, attempts.size)
    }

    @Test
    fun disconnectCancelsBackoffAndNeverRetriesOldAccount() = runTest {
        var attempts = 0
        val job = launch { recoverOmemoSession { attempts++; false } }
        runCurrent()
        job.cancelAndJoin()
        advanceTimeBy(120_000)
        assertEquals(1, attempts)
    }

    @Test
    fun replacingSessionCancelsInFlightAttemptBeforeNewSessionRecovers() = runTest {
        var exited = false
        val old = launch {
            recoverOmemoSession {
                try { awaitCancellation() } finally { exited = true }
            }
        }
        runCurrent()
        old.cancelAndJoin()
        assertTrue(exited)
        var attempts = 0
        recoverOmemoSession { attempts++; true }
        assertEquals(1, attempts)
    }

    @Test
    fun cancellationIsNotConvertedIntoAnEncryptionFailureOrRetry() = runTest {
        var attempts = 0
        try {
            recoverOmemoSession { attempts++; throw CancellationException("stopped") }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(1, attempts)
        }
    }
}
