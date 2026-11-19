package stapel.session.vault

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import stapel.session.SessionKind
import stapel.session.TokenPair
import kotlin.time.Instant

/** The on-disk shape of a [StoredSession] (inside whatever protection the platform vault adds). */
@Serializable
private class RecordV1(
    @SerialName("v") val version: Int = 1,
    @SerialName("access") val access: String,
    @SerialName("refresh") val refresh: String,
    @SerialName("access_expires_ms") val accessExpiresMs: Long? = null,
    @SerialName("kind") val kind: String,
    @SerialName("rotation_started_ms") val rotationStartedMs: Long? = null,
)

private val codecJson = Json { ignoreUnknownKeys = true }

internal fun encodeRecord(session: StoredSession): ByteArray = codecJson.encodeToString(
    RecordV1.serializer(),
    RecordV1(
        access = session.tokens.accessToken,
        refresh = session.tokens.refreshToken,
        accessExpiresMs = session.tokens.accessExpiresAt?.toEpochMilliseconds(),
        kind = session.kind.name,
        rotationStartedMs = session.rotationStartedAt?.toEpochMilliseconds(),
    ),
).encodeToByteArray()

/** Null when [bytes] are not a record this version understands. */
internal fun decodeRecord(bytes: ByteArray): StoredSession? {
    val record = try {
        codecJson.decodeFromString(RecordV1.serializer(), bytes.decodeToString())
    } catch (_: SerializationException) {
        return null
    } catch (_: IllegalArgumentException) {
        return null
    }
    if (record.version != 1) return null
    val kind = SessionKind.entries.firstOrNull { it.name == record.kind } ?: return null
    return StoredSession(
        TokenPair(record.access, record.refresh, record.accessExpiresMs?.let(Instant::fromEpochMilliseconds)),
        kind,
        record.rotationStartedMs?.let(Instant::fromEpochMilliseconds),
    )
}
