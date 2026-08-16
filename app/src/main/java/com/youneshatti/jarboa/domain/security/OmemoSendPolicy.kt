package com.youneshatti.jarboa.domain.security

enum class OmemoSendSafety {
    SAFE,
    NO_ENCRYPTED_RECIPIENT,
    ACCEPTED_DEVICE_SKIPPED,
}

object OmemoSendPolicy {
    fun evaluate(
        intendedRecipientIds: Set<Int>,
        skippedRecipientIds: Set<Int>,
        rejectedRecipientIds: Set<Int>,
    ): OmemoSendSafety {
        if ((intendedRecipientIds - skippedRecipientIds).isEmpty()) {
            return OmemoSendSafety.NO_ENCRYPTED_RECIPIENT
        }
        if ((skippedRecipientIds - rejectedRecipientIds).isNotEmpty()) {
            return OmemoSendSafety.ACCEPTED_DEVICE_SKIPPED
        }
        return OmemoSendSafety.SAFE
    }
}
