package stapel.auth.internal

import stapel.auth.api.model.AuthResponse
import stapel.auth.api.model.FirstLoginChallengeResponse
import stapel.auth.api.model.LoginResponse
import stapel.auth.api.model.SessionResponse
import stapel.auth.api.model.TOTPChallengeResponse
import stapel.auth.api.model.User
import stapel.auth.model.AuthCapabilities
import stapel.auth.model.AuthMethod
import stapel.auth.model.AuthMethodId
import stapel.auth.model.AuthType
import stapel.auth.model.AuthUser
import stapel.auth.model.DeviceType
import stapel.auth.model.FirstLoginStep
import stapel.auth.model.LoginChallenge
import stapel.auth.model.MethodInteraction
import stapel.auth.model.MethodPlacement
import stapel.auth.model.OtpParameters
import stapel.auth.model.PostureStage
import stapel.auth.model.SessionDevice
import stapel.auth.model.SignInKind
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import stapel.auth.api.model.AuthCapabilities as AuthCapabilitiesDto

/** The one place generated DTOs become domain types. */
internal fun User.toDomain(): AuthUser = AuthUser(
    id = id,
    username = username,
    email = email?.takeIf { it.isNotEmpty() },
    phone = phone?.takeIf { it.isNotEmpty() },
    authType = when (authType.wire) {
        "email" -> AuthType.Email
        "phone" -> AuthType.Phone
        "oauth" -> AuthType.OAuth
        "sso" -> AuthType.Sso
        "anonymous" -> AuthType.Anonymous
        "login" -> AuthType.Login
        else -> AuthType.Unknown
    },
    isGuest = isAnonymous,
    isEmailVerified = isEmailVerified,
    isPhoneVerified = isPhoneVerified,
    isStaff = isStaff,
    oauthProvider = oauthProvider?.takeIf { it.isNotEmpty() },
    avatarUrl = avatar?.takeIf { it.isNotEmpty() },
    createdAt = createdAt,
    lastLogin = lastLogin,
)

internal fun AuthResponse.kind(): SignInKind = when (status.wire) {
    "LOGGED_IN" -> SignInKind.LoggedIn
    "REGISTERED" -> SignInKind.Registered
    "MERGED" -> SignInKind.Merged
    "MODIFIED" -> SignInKind.Modified
    "REJECTED" -> SignInKind.Rejected
    else -> SignInKind.Other
}

/** The three shapes of the login union, decoded. */
internal sealed interface LoginStep {
    data class Session(val response: AuthResponse) : LoginStep

    data class Totp(val challenge: LoginChallenge) : LoginStep

    data class FirstLogin(val requires: FirstLoginStep, val challenge: LoginChallenge) : LoginStep

    /** A variant this client version does not know (the raw `status`). */
    data class Unknown(val status: String?) : LoginStep
}

internal fun LoginResponse.toStep(now: Instant): LoginStep = when (this) {
    is AuthResponse -> LoginStep.Session(this)
    is TOTPChallengeResponse -> LoginStep.Totp(LoginChallenge(challengeToken, now + expiresIn.seconds))
    is FirstLoginChallengeResponse -> LoginStep.FirstLogin(
        when (requires.wire) {
            "password_change" -> FirstLoginStep.PasswordChange
            "mfa_enroll" -> FirstLoginStep.MfaEnroll
            else -> FirstLoginStep.Unknown
        },
        LoginChallenge(challengeToken, now + expiresIn.seconds),
    )
    is LoginResponse.Unknown -> LoginStep.Unknown(raw["status"]?.toString())
}

internal fun methodId(wire: String): AuthMethodId = when (wire) {
    "email" -> AuthMethodId.Email
    "phone" -> AuthMethodId.Phone
    "password" -> AuthMethodId.Password
    "passkey" -> AuthMethodId.Passkey
    "qr" -> AuthMethodId.Qr
    "magic_link" -> AuthMethodId.MagicLink
    "oauth" -> AuthMethodId.OAuth
    "sso" -> AuthMethodId.Sso
    else -> AuthMethodId.Unknown
}

internal fun AuthCapabilitiesDto.toDomain(): AuthCapabilities = AuthCapabilities(
    methods = methods.map { m ->
        AuthMethod(
            id = methodId(m.id),
            wireId = m.id,
            enabled = m.enabled,
            placement = when (m.placement) {
                "main" -> MethodPlacement.Main
                "bottom" -> MethodPlacement.Bottom
                "overflow" -> MethodPlacement.Overflow
                else -> MethodPlacement.Unknown
            },
            order = m.order,
            interaction = when (m.interaction) {
                "inline" -> MethodInteraction.Inline
                "modal" -> MethodInteraction.Modal
                "redirect" -> MethodInteraction.Redirect
                else -> MethodInteraction.Unknown
            },
            iconSvg = m.iconSvg.takeIf { it.isNotEmpty() },
            mock = m.mock ?: false,
            canLogin = m.canLogin ?: true,
            canRegister = m.canRegister ?: false,
        )
    },
    passwordRegistration = registration.password,
    guestSessions = registration.anonymous,
    emailMock = login.emailMock ?: registration.emailMock ?: false,
    phoneMock = login.phoneMock ?: registration.phoneMock ?: false,
    otp = OtpParameters(
        emailCodeLength = otp.emailCodeLength,
        phoneCodeLength = otp.phoneCodeLength,
        totpCodeLength = otp.totpCodeLength,
        ttl = otp.ttlSeconds.seconds,
        resendCooldown = otp.resendCooldownSeconds.seconds,
    ),
    stage = when (posture.stage) {
        "prototype" -> PostureStage.Prototype
        "live" -> PostureStage.Live
        else -> PostureStage.Unknown
    },
)

internal fun SessionResponse.toDomain(): SessionDevice = SessionDevice(
    id = id,
    type = when (deviceType.lowercase()) {
        "phone", "mobile" -> DeviceType.Phone
        "tablet" -> DeviceType.Tablet
        "desktop" -> DeviceType.Desktop
        "api" -> DeviceType.Api
        else -> DeviceType.Unknown
    },
    name = deviceName,
    details = deviceDetails,
    ipAddress = ipAddress,
    createdAt = parseInstant(createdAt),
    lastUsedAt = parseInstant(lastUsedAt),
    isCurrent = isCurrent,
    isSuspicious = isSuspicious,
)

private fun parseInstant(text: String): Instant? = try {
    Instant.parse(text)
} catch (_: IllegalArgumentException) {
    null
}
