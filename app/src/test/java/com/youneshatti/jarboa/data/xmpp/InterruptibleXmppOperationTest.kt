package com.youneshatti.jarboa.data.xmpp

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class InterruptibleXmppOperationTest {
    @Test(timeout = 10_000)
    fun cancellationInterruptsBlockingWaitAndReleasesConnectionLock() = runBlocking {
        val mutex = Mutex()
        val entered = CompletableDeferred<Unit>()
        val interrupted = AtomicBoolean(false)
        val job = launch {
            interruptibleXmppOperation(mutex) {
                entered.complete(Unit)
                try { CountDownLatch(1).await() }
                catch (error: InterruptedException) { interrupted.set(true); throw error }
            }
        }
        withTimeout(5_000) { entered.await(); job.cancelAndJoin() }
        assertTrue(interrupted.get())
        assertEquals("ready", interruptibleXmppOperation(mutex) { "ready" })
    }

    @Test(timeout = 10_000)
    fun deadlineIncludesWaitingForConnectionLock() = runBlocking {
        val mutex = Mutex(locked = true)
        var ran = false
        val error = runCatching {
            interruptibleXmppOperation(mutex, 100) { ran = true }
        }.exceptionOrNull()
        assertTrue(error is IOException)
        assertFalse(ran)
        mutex.unlock()
        assertEquals("ready", interruptibleXmppOperation(mutex) { "ready" })
    }

    @Test(timeout = 10_000)
    fun deadlineInterruptsBlockingWorkInsteadOfLeavingAnOrphan() = runBlocking {
        val mutex = Mutex()
        val exited = AtomicBoolean(false)
        val error = runCatching {
            interruptibleXmppOperation(mutex, 500) {
                try { CountDownLatch(1).await() } finally { exited.set(true) }
            }
        }.exceptionOrNull()
        assertTrue(error is IOException)
        assertTrue(exited.get())
        assertFalse(mutex.isLocked)
    }
}
