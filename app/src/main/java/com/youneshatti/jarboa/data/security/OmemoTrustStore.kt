package com.youneshatti.jarboa.data.security

import android.content.Context
import com.youneshatti.jarboa.domain.model.OmemoDeviceInfo
import com.youneshatti.jarboa.domain.model.OmemoTrustLevel
import com.youneshatti.jarboa.domain.security.OmemoIdentityRecord
import com.youneshatti.jarboa.domain.security.OmemoTrustPolicy
import com.youneshatti.jarboa.domain.security.normalizedFingerprint
import org.jivesoftware.smackx.omemo.internal.OmemoDevice
import org.jivesoftware.smackx.omemo.trust.OmemoFingerprint
import org.jivesoftware.smackx.omemo.trust.OmemoTrustCallback
import org.jivesoftware.smackx.omemo.trust.TrustState

class OmemoTrustStore(context: Context) : OmemoTrustCallback {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    override fun getTrust(device: OmemoDevice, fingerprint: OmemoFingerprint): TrustState {
        val observed = observe(device.jid.toString(), device.deviceId, fingerprint.toString())
        return when (observed.trust) {
            OmemoTrustLevel.UNVERIFIED,
            OmemoTrustLevel.VERIFIED,
            -> TrustState.trusted
            OmemoTrustLevel.REJECTED -> TrustState.untrusted
            OmemoTrustLevel.CHANGED -> TrustState.undecided
        }
    }

    @Synchronized
    override fun setTrust(device: OmemoDevice, fingerprint: OmemoFingerprint, state: TrustState) {
        val level = when (state) {
            TrustState.trusted -> OmemoTrustLevel.VERIFIED
            TrustState.untrusted -> OmemoTrustLevel.REJECTED
            TrustState.undecided -> OmemoTrustLevel.CHANGED
        }
        val existing = read(device.jid.toString(), device.deviceId)
        write(
            device.jid.toString(),
            device.deviceId,
            if (level == OmemoTrustLevel.CHANGED) {
                OmemoTrustPolicy.observe(existing, fingerprint.toString())
            } else {
                OmemoTrustPolicy.decide(existing, fingerprint.toString(), level)
            },
        )
    }

    @Synchronized
    fun observe(jid: String, deviceId: Int, fingerprint: String): OmemoIdentityRecord {
        val existing = read(jid, deviceId)
        val observed = OmemoTrustPolicy.observe(existing, fingerprint)
        if (observed != existing) write(jid, deviceId, observed)
        return observed
    }

    @Synchronized
    fun decide(jid: String, deviceId: Int, fingerprint: String, trust: OmemoTrustLevel) {
        val record = OmemoTrustPolicy.decide(read(jid, deviceId), fingerprint, trust)
        write(jid, deviceId, record)
    }

    @Synchronized
    fun deviceInfo(jid: String, deviceId: Int, fingerprint: String): OmemoDeviceInfo {
        val record = observe(jid, deviceId, fingerprint)
        return OmemoDeviceInfo(
            deviceId = deviceId,
            fingerprint = record.fingerprint,
            previousFingerprint = record.previousFingerprint,
            trust = record.trust,
        )
    }

    fun clear() {
        preferences.edit().clear().commit()
    }

    private fun read(jid: String, deviceId: Int): OmemoIdentityRecord? {
        val value = preferences.getString(key(jid, deviceId), null) ?: return null
        val parts = value.split(SEPARATOR, limit = 3)
        if (parts.size < 2) return null
        val level = runCatching { OmemoTrustLevel.valueOf(parts[1]) }.getOrNull() ?: return null
        return OmemoIdentityRecord(
            fingerprint = parts[0].normalizedFingerprint(),
            trust = level,
            previousFingerprint = parts.getOrNull(2)?.takeIf(String::isNotBlank)?.normalizedFingerprint(),
        )
    }

    private fun write(jid: String, deviceId: Int, record: OmemoIdentityRecord) {
        val value = listOf(
            record.fingerprint.normalizedFingerprint(),
            record.trust.name,
            record.previousFingerprint.orEmpty().normalizedFingerprint(),
        ).joinToString(SEPARATOR)
        check(preferences.edit().putString(key(jid, deviceId), value).commit()) {
            "Jarboa could not persist the OMEMO trust decision."
        }
    }

    private fun key(jid: String, deviceId: Int) = "identity.${jid.lowercase()}.$deviceId"

    private companion object {
        const val PREFERENCES_NAME = "omemo_trust_v1"
        const val SEPARATOR = "|"
    }
}
