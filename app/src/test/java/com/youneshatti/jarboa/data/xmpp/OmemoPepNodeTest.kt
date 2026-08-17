package com.youneshatti.jarboa.data.xmpp

import org.junit.Assert.assertEquals
import org.junit.Test

class OmemoPepNodeTest {
    @Test
    fun `public nodes include device list and this device bundle`() {
        assertEquals(
            listOf(
                "eu.siacs.conversations.axolotl.devicelist",
                "eu.siacs.conversations.axolotl.bundles:42",
            ),
            omemoPepNodeIds(42),
        )
    }
}
