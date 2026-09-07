package com.youneshatti.jarboa.domain.model

enum class OmemoSessionStatus {
    INACTIVE,
    INITIALIZING,
    READY,
    FAILED,
}

data class OmemoSessionState(
    val status: OmemoSessionStatus,
    val ownFingerprint: String? = null,
    val detail: String? = null,
    val diagnosticCode: String? = null,
    val diagnosticReport: String? = null,
) {
    companion object {
        val Inactive = OmemoSessionState(OmemoSessionStatus.INACTIVE)
        val Initializing = OmemoSessionState(OmemoSessionStatus.INITIALIZING)
    }
}

enum class OmemoTrustLevel {
    UNVERIFIED,
    VERIFIED,
    REJECTED,
    CHANGED,
}

data class OmemoDeviceInfo(
    val deviceId: Int,
    val fingerprint: String,
    val previousFingerprint: String? = null,
    val trust: OmemoTrustLevel,
)

enum class OmemoContactStatus {
    CHECKING,
    READY,
    UNAVAILABLE,
}

data class OmemoContactSecurity(
    val jid: String,
    val status: OmemoContactStatus,
    val devices: List<OmemoDeviceInfo> = emptyList(),
    val detail: String? = null,
) {
    val hasChangedIdentity: Boolean
        get() = devices.any { it.trust == OmemoTrustLevel.CHANGED }

    val usableDevices: List<OmemoDeviceInfo>
        get() = devices.filter { it.trust == OmemoTrustLevel.UNVERIFIED || it.trust == OmemoTrustLevel.VERIFIED }

    val canSend: Boolean
        get() = status == OmemoContactStatus.READY && !hasChangedIdentity && usableDevices.isNotEmpty()

    val allUsableDevicesVerified: Boolean
        get() = usableDevices.isNotEmpty() && usableDevices.all { it.trust == OmemoTrustLevel.VERIFIED }

    companion object {
        fun checking(jid: String) = OmemoContactSecurity(jid, OmemoContactStatus.CHECKING)
    }
}

enum class MessageEncryption {
    LEGACY_PLAINTEXT,
    UNENCRYPTED_INCOMING,
    OMEMO_UNVERIFIED,
    OMEMO_VERIFIED,
    OMEMO_KEY_CHANGED,
}

data class OmemoSendResult(
    val encryption: MessageEncryption,
)
