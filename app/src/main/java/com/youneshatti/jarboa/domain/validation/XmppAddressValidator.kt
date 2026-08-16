package com.youneshatti.jarboa.domain.validation

import java.net.IDN

object XmppAddressValidator {
    private const val MAX_JID_LENGTH = 3071

    fun validate(input: String): JidValidationResult {
        val candidate = input.trim()
        if (candidate.isEmpty()) return JidValidationResult.Invalid("Enter your XMPP address.")
        if (candidate.length > MAX_JID_LENGTH) return JidValidationResult.Invalid("The XMPP address is too long.")
        if ('/' in candidate) return JidValidationResult.Invalid("Use a bare address without a device resource.")
        if (candidate.any(Char::isWhitespace)) return JidValidationResult.Invalid("The XMPP address cannot contain spaces.")
        if (candidate.count { it == '@' } != 1) {
            return JidValidationResult.Invalid("Use an address like name@example.org.")
        }

        val localpart = candidate.substringBefore('@')
        val domain = candidate.substringAfter('@')
        if (localpart.isBlank() || domain.isBlank()) {
            return JidValidationResult.Invalid("Both the account name and server domain are required.")
        }
        if (localpart.any { it.code < 0x20 || it in "\"&'/:<>@" }) {
            return JidValidationResult.Invalid("The account name contains unsupported characters.")
        }

        val asciiDomain = runCatching { IDN.toASCII(domain, IDN.USE_STD3_ASCII_RULES) }.getOrNull()
            ?: return JidValidationResult.Invalid("The server domain is invalid.")
        if (asciiDomain.isBlank() || asciiDomain.length > 253 || asciiDomain.split('.').any { it.isBlank() }) {
            return JidValidationResult.Invalid("The server domain is invalid.")
        }

        return JidValidationResult.Valid("$localpart@${asciiDomain.lowercase()}")
    }
}

sealed interface JidValidationResult {
    data class Valid(val normalizedJid: String) : JidValidationResult
    data class Invalid(val message: String) : JidValidationResult
}

