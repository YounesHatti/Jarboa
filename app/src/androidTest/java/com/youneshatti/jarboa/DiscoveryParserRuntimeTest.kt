package com.youneshatti.jarboa

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.youneshatti.jarboa.data.xmpp.ValidatingDiscoverInfoProvider
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.StanzaBuilder
import org.jivesoftware.smack.provider.IqProvider
import org.jivesoftware.smack.util.PacketParserUtils
import org.jivesoftware.smackx.disco.packet.DiscoverInfo
import org.jivesoftware.smackx.disco.provider.DiscoverInfoProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run both the original failure and the replacement in the optimized Android app. */
@RunWith(AndroidJUnit4::class)
class DiscoveryParserRuntimeTest {
    private fun parse(provider: IqProvider<DiscoverInfo>, children: String): DiscoverInfo =
        provider.parse(
            PacketParserUtils.getParserFor("<query xmlns='${DiscoverInfo.NAMESPACE}' node='fixture'>$children</query>"),
            StanzaBuilder.buildIqData("disco-fixture").ofType(IQ.Type.result),
        )

    @Test fun reproducesOriginalFailureAndDiscardsOnlyInvalidEntries() {
        val fixtures = listOf(
            "<identity type='file'/>" to "IDENTITY_CATEGORY",
            "<identity category='' type='file'/>" to "IDENTITY_CATEGORY",
            "<identity category='store'/>" to "IDENTITY_TYPE",
            "<identity category='store' type=''/>" to "IDENTITY_TYPE",
            "<feature/>" to "FEATURE_VAR",
            "<feature var=''/>" to "FEATURE_VAR",
        )
        val valid = "<identity category='store' type='file' name='Upload' xml:lang='en'/>" +
            "<feature var='urn:xmpp:http:upload:0'/>" +
            "<x xmlns='jabber:x:data' type='result'><field var='FORM_TYPE' type='hidden'>" +
            "<value>urn:xmpp:http:upload:0</value></field><field var='max-file-size'>" +
            "<value>26214416</value></field></x>"
        for ((invalid, expectedField) in fixtures) {
            val original = runCatching { parse(DiscoverInfoProvider(), invalid + valid) }.exceptionOrNull()
            assertTrue("Upstream must reproduce the phone's failure: $invalid", original is IllegalArgumentException)
            val result = parse(ValidatingDiscoverInfoProvider(), invalid + valid) as ValidatingDiscoverInfoProvider.Result
            assertEquals(setOf(expectedField), result.invalidFields.map { it.name }.toSet())
            assertEquals("disco-fixture", result.stanzaId)
            assertEquals(IQ.Type.result, result.type)
            assertEquals("fixture", result.node)
            assertTrue(result.hasIdentity("store", "file"))
            assertEquals(1, result.identities.size)
            assertEquals("Upload", result.identities.single().name)
            assertEquals("en", result.identities.single().language)
            assertEquals(1, result.features.size)
            assertTrue(result.containsFeature("urn:xmpp:http:upload:0"))
            assertNotNull(result.getExtension<org.jivesoftware.smack.packet.ExtensionElement>("x", "jabber:x:data"))
            assertTrue(result.toXML().toString().contains("26214416"))
        }
    }

    @Test fun invalidOrWrappedEntriesCannotInventCapabilitiesAndMalformedXmlStillFails() {
        val provider = ValidatingDiscoverInfoProvider()
        val result = parse(provider, "<identity/><feature/><unknown><feature var='urn:xmpp:http:upload:0'/></unknown>")
        assertTrue(result.identities.isEmpty())
        assertTrue(result.features.isEmpty())
        assertFalse(result.containsFeature("urn:xmpp:http:upload:0"))
        assertTrue(runCatching { parse(provider, "<feature var='broken'>") }.isFailure)
        val valid = parse(provider, "<identity category='client' type='phone'/><feature var='urn:xmpp:ping'/>")
        assertTrue((valid as ValidatingDiscoverInfoProvider.Result).invalidFields.isEmpty())
        assertTrue(valid.containsFeature("urn:xmpp:ping"))
    }
}
