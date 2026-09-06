package com.youneshatti.jarboa

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.youneshatti.jarboa.data.security.OmemoTrustStore
import com.youneshatti.jarboa.data.xmpp.SmackXmppClient
import com.youneshatti.jarboa.data.xmpp.XmppEvent
import com.youneshatti.jarboa.domain.model.AccountConfig
import com.youneshatti.jarboa.domain.model.FailureReason
import com.youneshatti.jarboa.domain.model.MessageEncryption
import com.youneshatti.jarboa.domain.model.OmemoSessionStatus
import com.youneshatti.jarboa.domain.model.OmemoTrustLevel
import com.youneshatti.jarboa.domain.model.XmppConnectionState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Executes the app's real XMPP client in an R8-optimized APK, against an isolated TLS server. */
@RunWith(AndroidJUnit4::class)
class OmemoRuntimeTest {
    @Test
    fun cryptoStorageFailureKeepsLoginConnectedAndSendingBlocked() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val invalidStore = File(context.noBackupFilesDir, "ci-not-a-directory")
        invalidStore.writeText("test fixture")
        val client = SmackXmppClient(OmemoTrustStore(context), invalidStore)
        try {
            client.connect(AccountConfig("alice@jarboa.test", "10.0.2.2", 5222), "ci-alice-password".toCharArray())
            assertTrue(client.connectionState.value.toString(), client.connectionState.value is XmppConnectionState.Connected)
            assertEquals(OmemoSessionStatus.FAILED, client.omemoState.value.status)
            assertFalse(client.retryEncryption())
            assertTrue(client.connectionState.value is XmppConnectionState.Connected)
            assertTrue(runCatching { client.sendDirectMessage("bob@jarboa.test", "Must never be sent", "ci-blocked") }.isFailure)
        } finally {
            client.disconnect()
            invalidStore.delete()
        }
    }

    @Test
    fun wrongPasswordAndWrongCertificateAreRejected() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = SmackXmppClient(OmemoTrustStore(context), File(context.noBackupFilesDir, "omemo"))
        try {
            val password = "wrong-password".toCharArray()
            assertTrue(runCatching { client.connect(AccountConfig("alice@jarboa.test", "10.0.2.2", 5222), password) }.isFailure)
            assertEquals(FailureReason.AUTHENTICATION, (client.connectionState.value as XmppConnectionState.Failed).reason)
            assertTrue(password.all { it == '\u0000' })
            // The CA is trusted by the CI-only variant, but its certificate names jarboa.test.
            assertTrue(runCatching { client.connect(AccountConfig("alice@wrong.test", "10.0.2.2", 5222), "unused".toCharArray()) }.isFailure)
            assertEquals(FailureReason.TLS, (client.connectionState.value as XmppConnectionState.Failed).reason)
        } finally {
            client.disconnect()
        }
    }

    @Test
    fun loginEncryptReceiveReconnectAndRejectUntrustedDevices() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.noBackupFilesDir, "omemo")
        val alice = SmackXmppClient(OmemoTrustStore(context), directory)
        val bob = SmackXmppClient(OmemoTrustStore(context), directory)
        fun config(name: String) = AccountConfig("$name@jarboa.test", "10.0.2.2", 5222)
        try {
            alice.connect(config("alice"), "ci-alice-password".toCharArray())
            bob.connect(config("bob"), "ci-bob-password".toCharArray())
            assertTrue(alice.connectionState.value.toString(), alice.connectionState.value is XmppConnectionState.Connected)
            assertTrue(bob.connectionState.value.toString(), bob.connectionState.value is XmppConnectionState.Connected)
            assertEquals(alice.omemoState.value.toString(), OmemoSessionStatus.READY, alice.omemoState.value.status)
            assertEquals(bob.omemoState.value.toString(), OmemoSessionStatus.READY, bob.omemoState.value.status)
            val fingerprint = alice.omemoState.value.ownFingerprint
            val bobReceived = async {
                withTimeout(30_000) { bob.events.filterIsInstance<XmppEvent.IncomingMessage>().first { it.stanzaId == "ci-forward" } }
            }
            val sent = alice.sendDirectMessage("bob@jarboa.test", "Encrypted hello from Alice", "ci-forward")
            assertEquals(MessageEncryption.OMEMO_UNVERIFIED, sent.encryption)
            assertEquals("Encrypted hello from Alice", bobReceived.await().body)
            assertEquals(MessageEncryption.OMEMO_UNVERIFIED, bobReceived.await().encryption)
            val aliceReceived = async {
                withTimeout(30_000) { alice.events.filterIsInstance<XmppEvent.IncomingMessage>().first { it.stanzaId == "ci-reply" } }
            }
            bob.sendDirectMessage("alice@jarboa.test", "Encrypted reply from Bob", "ci-reply")
            assertEquals("Encrypted reply from Bob", aliceReceived.await().body)
            assertEquals(MessageEncryption.OMEMO_UNVERIFIED, aliceReceived.await().encryption)

            alice.disconnect()
            bob.sendDirectMessage("alice@jarboa.test", "While Alice was offline", "ci-offline")
            val offline = async {
                withTimeout(30_000) { alice.events.filterIsInstance<XmppEvent.IncomingMessage>().first { it.stanzaId == "ci-offline" } }
            }
            alice.connect(config("alice"), "ci-alice-password".toCharArray())
            assertEquals("While Alice was offline", offline.await().body)
            assertEquals(alice.omemoState.value.toString(), OmemoSessionStatus.READY, alice.omemoState.value.status)
            assertEquals("Reconnecting must retain the identity", fingerprint, alice.omemoState.value.ownFingerprint)
            val afterReconnect = async {
                withTimeout(30_000) { bob.events.filterIsInstance<XmppEvent.IncomingMessage>().first { it.stanzaId == "ci-reconnect" } }
            }
            alice.sendDirectMessage("bob@jarboa.test", "After reconnect", "ci-reconnect")
            assertEquals("After reconnect", afterReconnect.await().body)
            assertTrue(alice.retryEncryption())
            assertTrue(alice.connectionState.value is XmppConnectionState.Connected)
            assertEquals(fingerprint, alice.omemoState.value.ownFingerprint)

            val devices = alice.loadContactSecurity("bob@jarboa.test")
            assertTrue(devices.toString(), devices.canSend)
            devices.devices.forEach {
                alice.setDeviceTrust("bob@jarboa.test", it.deviceId, it.fingerprint, OmemoTrustLevel.REJECTED)
            }
            val rejected = runCatching { alice.sendDirectMessage("bob@jarboa.test", "Must never be sent", "ci-blocked") }
            assertTrue("Rejected devices must block sending", rejected.isFailure)
        } finally {
            alice.disconnect()
            bob.disconnect()
        }
    }
}
