package stapel.auth.platform

import stapel.auth.model.AuthMethodId

/**
 * A system-browser auth session (Custom Tabs / ASWebAuthenticationSession / desktop browser).
 * Seam only in v0: no flow uses it until the backend can hand a browser sign-in back to an app
 * (allow-listed native redirect URIs + a PKCE-bound grant); see [AuthPlatform.completable].
 */
public interface ExternalUserAgent {
    /** Opens [url] and resumes with the URI the browser was redirected to, or null when the user closed it. */
    public suspend fun authenticate(url: String, redirectUri: String): String?
}

/** WebAuthn ceremonies over the platform credential manager. Seam only in v0. */
public interface PasskeyAuthenticator {
    /** Whether the platform can perform a ceremony now. */
    public val isAvailable: Boolean

    /** Creates a credential from the server's creation options (JSON in, JSON out); null = cancelled. */
    public suspend fun create(creationOptionsJson: String): String?

    /** Gets an assertion for the server's request options (JSON in, JSON out); null = cancelled. */
    public suspend fun get(requestOptionsJson: String): String?
}

/** A provider SDK that yields a token the token-body OAuth endpoint accepts. Seam only in v0. */
public interface NativeIdentityProvider {
    /** Provider id as the server names it. */
    public val providerId: String

    /** The provider access token, or null when the user cancelled. */
    public suspend fun acquireToken(): String?
}

/**
 * Optional platform capabilities; every member defaults to "absent". Adapter artifacts contribute
 * implementations; the domain library never depends on a platform SDK.
 */
public class AuthPlatform(
    /** System browser, for redirect-based methods. */
    public val browser: ExternalUserAgent? = null,
    /** Passkeys. */
    public val passkeys: PasskeyAuthenticator? = null,
    /** Native social providers by id. */
    public val identityProviders: Map<String, NativeIdentityProvider> = emptyMap(),
) {
    /**
     * Whether this client can finish [method] end to end. v0 finishes e-mail and phone codes and
     * passwords. Passkeys, OAuth, magic links, SSO and QR need backend hand-off work (ruling R14) and
     * the adapter flows of later slices, so they are never offered — even when an adapter is present
     * and the server enables them. A method that cannot be completed is never rendered.
     */
    public fun completable(method: AuthMethodId): Boolean = method in V0_COMPLETABLE

    private companion object {
        val V0_COMPLETABLE = setOf(AuthMethodId.Email, AuthMethodId.Phone, AuthMethodId.Password)
    }
}
