package com.youneshatti.jarboa.domain.security

import com.youneshatti.jarboa.domain.model.OmemoTrustLevel

data class OmemoIdentityRecord(
    val fingerprint: String,
    val trust: OmemoTrustLevel,
    val previousFingerprint: String? = null,
)

object OmemoTrustPolicy {
    fun observe(existing: OmemoIdentityRecord?, fingerprint: String): OmemoIdentityRecord {
        val normalized = fingerprint.normalizedFingerprint()
        if (existing == null) {
            return OmemoIdentityRecord(normalized, OmemoTrustLevel.UNVERIFIED)
        }
        if (existing.fingerprint.normalizedFingerprint() == normalized) return existing.copy(fingerprint = normalized)
        return OmemoIdentityRecord(
            fingerprint = normalized,
            trust = OmemoTrustLevel.CHANGED,
            previousFingerprint = existing.fingerprint.normalizedFingerprint(),
        )
    }

    fun decide(
        existing: OmemoIdentityRecord?,
        fingerprint: String,
        trust: OmemoTrustLevel,
    ): OmemoIdentityRecord {
        require(trust == OmemoTrustLevel.VERIFIED || trust == OmemoTrustLevel.REJECTED)
        val normalized = fingerprint.normalizedFingerprint()
        val previous = existing?.takeIf { it.fingerprint.normalizedFingerprint() != normalized }?.fingerprint
            ?: existing?.previousFingerprint
        return OmemoIdentityRecord(normalized, trust, previous?.normalizedFingerprint())
    }
}

fun String.normalizedFingerprint(): String = filterNot(Char::isWhitespace).lowercase()
