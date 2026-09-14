package com.youneshatti.jarboa.data.xmpp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jivesoftware.smack.XMPPException

internal enum class ConnectionDiagnosticEvent {
    CONNECT_START, TLS_CONNECTED, LOGIN_START, LOGIN_WAITING, AUTHENTICATED, LOGIN_RETURNED,
    CONNECT_FAILED, CONNECTION_CLOSED, CONNECTION_ERROR, RECONNECT_FAILED, STANZA_PARSE_FAILED,
    UPLOAD_DISCOVERY, UPLOAD_PROBE_FAILED, UPLOAD_SLOT, UPLOAD_SLOT_RECEIVED, UPLOAD_FAILED,
}

/** Memory only, across reconnects but not sign-out/process death. Never accepts arbitrary text. */
internal class ConnectionDiagnostics(private val nanoTime: () -> Long = System::nanoTime) {
    private val started = nanoTime()
    private val recent = ArrayDeque<String>()
    private var lastFailure: String? = null
    private var lastParserFailure: String? = null
    private var lastConnectionFailure: String? = null
    private val mutableReport = MutableStateFlow("")
    val report = mutableReport.asStateFlow()

    @Synchronized
    fun record(
        event: ConnectionDiagnosticEvent,
        connected: Boolean,
        authenticated: Boolean,
        error: Throwable? = null,
        waitingStack: Array<StackTraceElement> = emptyArray(),
    ) {
        val entry = buildString {
            append("+").append((nanoTime() - started) / 1_000_000_000).append("s ").append(event.name)
            append(" transport=").append(connected).append(" authenticated=").append(authenticated)
            if (error != null) append('\n').append(safeConnectionFailure(error))
            if (waitingStack.isNotEmpty()) append('\n').append(safeLibraryLocations(waitingStack.toList()))
        }
        if (error != null) lastFailure = entry
        if (event == ConnectionDiagnosticEvent.STANZA_PARSE_FAILED) lastParserFailure = entry
        if (event in connectionFailures) lastConnectionFailure = entry
        recent.addLast(entry)
        while (recent.size > 12) recent.removeFirst()
        mutableReport.value = buildString {
            lastParserFailure?.let { append("Last parser failure:\n").append(it).append("\n\n") }
            lastConnectionFailure?.let { append("Last connection failure:\n").append(it).append("\n\n") }
            lastFailure?.let { append("Last failure:\n").append(it).append("\n\n") }
            append("Recent events (seconds since app session started):\n")
            append(recent.joinToString("\n"))
        }
    }

    @Synchronized
    fun clear() {
        recent.clear()
        lastFailure = null
        lastParserFailure = null
        lastConnectionFailure = null
        mutableReport.value = ""
    }
}

/** Types, protocol condition enums, and library code locations only: never messages or stanza XML. */
internal fun safeConnectionFailure(error: Throwable): String {
    val causes = generateSequence(error) { it.cause }.take(5).toList()
    return buildString {
        append("Types: ").append(causes.joinToString(" -> ") { it.javaClass.name })
        causes.filterIsInstance<XMPPException.XMPPErrorException>().firstOrNull()?.let {
            append("\nIQ condition: ").append(it.stanzaError.condition.name)
        }
        causes.filterIsInstance<XMPPException.StreamErrorException>().firstOrNull()?.let {
            append("\nStream condition: ").append(it.streamError.condition.name)
        }
        // Prefer the root cause. File names, line numbers and arbitrary stack elements are excluded.
        append('\n').append(safeLibraryLocations(causes.asReversed().flatMap { it.stackTrace.toList() }))
    }
}

private fun safeLibraryLocations(stack: List<StackTraceElement>): String {
    // R8 may rename extension classes. Retain their code symbols for mapping-file lookup,
    // without adding keep rules that would change Smack's reflection-based startup.
    val frames = stack.filterNot {
        it.className.startsWith("java.") || it.className.startsWith("kotlin.") ||
            it.className.startsWith("kotlinx.") || it.className.startsWith("android.") ||
            it.className.startsWith("dalvik.")
    }.filter {
        it.className.length <= 200 && it.methodName.length <= 100 &&
            safeCodeName.matches(it.className) && safeCodeName.matches(it.methodName)
    }.distinctBy { it.className + "." + it.methodName }.sortedBy {
        when {
            it.className.startsWith("org.jivesoftware.smack") -> 0
            it.className.startsWith("com.youneshatti.jarboa") -> 1
            else -> 2
        }
    }.take(8)
    return "Library locations: " + if (frames.isEmpty()) "unavailable" else
        frames.joinToString(" -> ") { it.className + "." + it.methodName }
}

private val safeCodeName = Regex("[A-Za-z0-9_.$<>]+")
private val connectionFailures = setOf(
    ConnectionDiagnosticEvent.CONNECTION_ERROR, ConnectionDiagnosticEvent.CONNECT_FAILED,
    ConnectionDiagnosticEvent.RECONNECT_FAILED,
)
