package com.youneshatti.jarboa

import android.content.Intent
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.youneshatti.jarboa.notifications.JarboaNotifier
import com.youneshatti.jarboa.ui.forChatContent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatPresentationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun notificationTargetsAreDistinctAndReusableForEachChat() {
        val first = JarboaNotifier.launchIntent(context, "alice@example.org")
        val second = JarboaNotifier.launchIntent(context, "bob@example.org")
        assertFalse(first.filterEquals(second))
        assertFalse(first.filterEquals(JarboaNotifier.launchIntent(context, null)))
        assertTrue(first.filterEquals(JarboaNotifier.launchIntent(context, "alice@example.org")))
        assertEquals("alice@example.org", first.getStringExtra(JarboaNotifier.EXTRA_CONVERSATION))
        assertTrue(first.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertTrue(first.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertEquals(MainActivity::class.java.name, first.component!!.className)
    }

    @Test
    fun arabicAndEnglishResolveByContentOnEitherPhoneLayout() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                val measurer = TextMeasurer(createFontFamilyResolver(context), Density(1f), layout)
                val style = TextStyle(fontSize = 18.sp).forChatContent()
                for (text in listOf("مرحبا بالعالم", "مرحبا Jarboa 123")) {
                    val result = measurer.measure(text, style, constraints = Constraints(minWidth = 400, maxWidth = 400))
                    assertEquals(ResolvedTextDirection.Rtl, result.getParagraphDirection(0))
                    assertTrue(result.getLineLeft(0) > 0f)
                    assertEquals(400f, result.getLineRight(0), 1f)
                }
                val english = measurer.measure("Hello مرحبا", style, constraints = Constraints(minWidth = 400, maxWidth = 400))
                assertEquals(ResolvedTextDirection.Ltr, english.getParagraphDirection(0))
                assertEquals(0f, english.getLineLeft(0), 1f)
            }
        }
    }
}
