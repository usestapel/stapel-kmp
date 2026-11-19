package stapel.session

import kotlinx.coroutines.test.TestScope
import stapel.testing.FakeConnectivity
import kotlinx.coroutines.test.runTest
import stapel.session.vault.StoredSession
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountIdTest {
    private fun token(payload: String): String =
        "eyJhbGciOiJSUzI1NiJ9." +
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(payload.encodeToByteArray()) + ".sig"

    // The shape the backend mints: the serialized user (user_id = str(pk)) plus token_type/exp/iat/jti/iss.
    private val minted = token(
        """{"user_id":"42","email":"a@example.com","username":"a","is_anonymous":false,""" +
            """"token_type":"access","exp":1900000000,"iat":1800000000,"jti":"j","iss":"auth"}""",
    )

    @Test
    fun readsTheUserIdClaim() {
        assertEquals("42", jwtAccountId(minted))
        assertEquals("7", jwtAccountId(token("""{"user_id":7}""")))
    }

    @Test
    fun otherClaimsAndMalformedTokensNameNoAccount() {
        // Inversion: a `sub` claim is not the account (the backend does not mint one).
        assertNull(jwtAccountId(token("""{"sub":"42"}""")))
        assertNull(jwtAccountId(token("""{"user_id":""}""")))
        assertNull(jwtAccountId(token("""{"user_id":null}""")))
        assertNull(jwtAccountId("access-A"))
        assertNull(jwtAccountId("a.%%%.c"))
    }

    private fun TestScope.manager(initial: StoredSession?) = SessionManager(
        SessionDependencies(RecordingVault(initial), backgroundScope, FakeClock(), FakeConnectivity()),
        ScriptedRefresher { RefreshOutcome.Rotated(TokenPair(minted, "r2")) },
        SessionConfig(),
    )

    @Test
    fun restoredAndAdoptedSessionsPublishTheAccount() = runTest {
        val restored = manager(StoredSession(TokenPair(minted, "r"), SessionKind.Authenticated))
        restored.restore()
        assertEquals(SessionState.Authenticated(1, "42"), restored.state.value)

        val adopted = manager(null)
        adopted.restore()
        adopted.adopt(TokenPair(token("""{"user_id":"g-9","is_anonymous":true}"""), "r"), SessionKind.Anonymous)
        assertEquals(SessionState.Anonymous(1, "g-9"), adopted.state.value)
        adopted.adopt(TokenPair("opaque", "r"), SessionKind.Authenticated)
        assertEquals(SessionState.Authenticated(2, null), adopted.state.value)
    }
}
