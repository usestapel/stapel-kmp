package stapel.session

import kotlinx.coroutines.CompletableDeferred
import stapel.runtime.SessionTelemetryEvent
import stapel.runtime.StapelTelemetry
import stapel.session.vault.StoredSession
import stapel.testing.TestClock
import stapel.session.vault.TokenVault
import stapel.session.vault.VaultRead
import kotlin.io.encoding.Base64
import kotlin.time.Instant

/** The session tests' clock: stapel-testing's [TestClock] at a fixed start. */
internal fun FakeClock(): TestClock = TestClock(Instant.fromEpochSeconds(1_800_000_000))

/** A JWT-shaped token whose payload carries [exp] (signature is not checked by the client). */
internal fun jwt(name: String, exp: Instant): String {
    val payload = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        .encode("{\"sub\":\"$name\",\"exp\":${exp.epochSeconds}}".encodeToByteArray())
    return "eyJhbGciOiJIUzI1NiJ9.$payload.sig-$name"
}

/** A JWT-shaped token carrying `iat` and `exp`, like the backend's access tokens. */
internal fun jwt(name: String, iat: Instant, exp: Instant): String {
    val payload = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        .encode("{\"user_id\":\"$name\",\"iat\":${iat.epochSeconds},\"exp\":${exp.epochSeconds}}".encodeToByteArray())
    return "eyJhbGciOiJIUzI1NiJ9.$payload.sig-$name"
}

internal fun pair(name: String, exp: Instant? = null) =
    TokenPair(if (exp == null) "access-$name" else jwt(name, exp), "refresh-$name")

/** A vault that records every operation; [writeGate] can hold writes of a chosen pair. */
internal class RecordingVault(initial: StoredSession? = null) : TokenVault {
    var record: StoredSession? = initial
    val log = mutableListOf<String>()
    var unreadable: String? = null
    var holdWriteOf: String? = null
    val writeGate = CompletableDeferred<Unit>()

    override suspend fun read(): VaultRead {
        log += "read"
        unreadable?.let { return VaultRead.Unreadable(it) }
        return record?.let { VaultRead.Present(it) } ?: VaultRead.Empty
    }

    override suspend fun write(session: StoredSession) {
        if (holdWriteOf != null && session.tokens.accessToken == holdWriteOf) writeGate.await()
        log += "write:${session.tokens.refreshToken}:${if (session.rotationStartedAt != null) "marker" else "clean"}"
        record = session
    }

    override suspend fun clear() {
        log += "clear"
        record = null
    }
}

/** A refresher scripted per call; [gate] holds every call until completed. */
internal class ScriptedRefresher(var answer: suspend (TokenPair) -> RefreshOutcome) : TokenRefresher {
    var calls = 0
    val seen = mutableListOf<TokenPair>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun refresh(current: TokenPair): RefreshOutcome {
        calls++
        seen += current
        gate?.await()
        return answer(current)
    }
}

internal class RecordingTelemetry : StapelTelemetry {
    val events = mutableListOf<SessionTelemetryEvent>()

    override fun sessionEvent(event: SessionTelemetryEvent) {
        events += event
    }
}
