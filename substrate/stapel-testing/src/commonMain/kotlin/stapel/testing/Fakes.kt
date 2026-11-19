package stapel.testing

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import stapel.config.ClientInfo
import stapel.config.ClientPlatform
import stapel.config.Posture
import stapel.config.ServiceEndpoint
import stapel.config.StapelEnvironment
import stapel.result.text.LocaleTag
import stapel.runtime.AppLifecycle
import stapel.runtime.AppPhase
import stapel.runtime.Connectivity
import stapel.runtime.IdGenerator
import stapel.runtime.LocaleProvider
import stapel.runtime.LogLevel
import stapel.runtime.NetworkStatus
import stapel.runtime.StapelDispatchers
import stapel.runtime.StapelLogger
import stapel.runtime.TimeZoneProvider
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** A clock that moves only when told to. */
public class TestClock(start: Instant = Instant.fromEpochMilliseconds(DEFAULT_EPOCH_MILLIS)) : Clock {
    private var current: Instant = start

    /** The current instant. */
    override fun now(): Instant = current

    /** Moves the clock forward by [by]. */
    public fun advance(by: Duration) {
        current += by
    }

    /** Sets the clock to [instant]. */
    public fun set(instant: Instant) {
        current = instant
    }

    /** Holder of the default start. */
    public companion object {
        /** 2026-01-01T00:00:00Z: the default start, so tests never depend on the real date. */
        public const val DEFAULT_EPOCH_MILLIS: Long = 1_767_225_600_000
    }
}

/**
 * A clock that follows this test's virtual time: `start` plus the scheduler's elapsed time, so
 * `delay`/`advanceTimeBy` and timestamps agree.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
public fun TestScope.virtualTimeClock(start: Instant = Instant.fromEpochMilliseconds(TestClock.DEFAULT_EPOCH_MILLIS)): Clock {
    val scheduler = testScheduler
    return object : Clock {
        /** The current instant. */
        override fun now(): Instant = start + scheduler.currentTime.milliseconds
    }
}

/**
 * Every dispatcher is [dispatcher], so all substrate work runs on the test scheduler.
 *
 * @property dispatcher the one dispatcher behind all three.
 */
public class TestDispatchers(public val dispatcher: TestDispatcher) : StapelDispatchers {
    /** The test dispatcher. */
    override val main: CoroutineDispatcher get() = dispatcher
    /** The test dispatcher. */
    override val default: CoroutineDispatcher get() = dispatcher
    /** The test dispatcher. */
    override val io: CoroutineDispatcher get() = dispatcher
}

/** UUID-shaped ids `00000000-0000-4000-8000-000000000001`, `…002`, … in order. */
public class SequentialIdGenerator : IdGenerator {
    private var next = 1L

    /** The next id in sequence. */
    override fun uuid(): String = "00000000-0000-4000-8000-" + (next++).toString().padStart(12, '0')
}

/**
 * One record written to a [RecordingLogger].
 *
 * @property level the record's level.
 * @property tag the record's tag.
 * @property message the message text.
 * @property error the attached error, if any.
 * @property fields the structured fields.
 */
public data class LogRecord(
    public val level: LogLevel,
    public val tag: String,
    public val message: String,
    public val error: Throwable?,
    public val fields: Map<String, String>,
)

/** Keeps every record at [minLevel] and above, for assertions (including "no secret was logged"). */
public class RecordingLogger(private val minLevel: LogLevel = LogLevel.Verbose) : StapelLogger {
    private val recorded = mutableListOf<LogRecord>()

    /** The records so far, oldest first. */
    public val records: List<LogRecord> get() = recorded.toList()

    /** True for the minimum level and above. */
    override fun isEnabled(level: LogLevel, tag: String): Boolean = level >= minLevel

    /** Records the entry when its level is enabled. */
    override fun log(level: LogLevel, tag: String, message: String, error: Throwable?, fields: Map<String, String>) {
        if (level >= minLevel) recorded += LogRecord(level, tag, message, error, fields)
    }

    /** True when [text] appears in any message or field value — use it to prove a secret never reached the log. */
    public fun contains(text: String): Boolean =
        recorded.any { r -> text in r.message || r.fields.values.any { text in it } || (r.error?.toString()?.contains(text) == true) }

    /** Forgets every record. */
    public fun clear() {
        recorded.clear()
    }
}

/** Connectivity a test sets by hand; counts the transport reports it receives. */
public class FakeConnectivity(initial: NetworkStatus = NetworkStatus.Online()) : Connectivity {
    private val state = MutableStateFlow(initial)

    /** The status last [set]. */
    override val status: StateFlow<NetworkStatus> = state.asStateFlow()

    /** How many times [reportReachable] was called. */
    public var reachableReports: Int = 0
        private set

    /** How many times [reportUnreachable] was called. */
    public var unreachableReports: Int = 0
        private set

    /** Sets the status. */
    public fun set(status: NetworkStatus) {
        state.value = status
    }

    /** Counts the report; the status changes only through [set]. */
    override fun reportReachable() {
        reachableReports++
    }

    /** Counts the report; the status changes only through [set]. */
    override fun reportUnreachable() {
        unreachableReports++
    }
}

/** A locale provider starting at [device]; [setAppLocale] overrides it like the real one. */
public class FixedLocaleProvider(device: LocaleTag = LocaleTag("en")) : LocaleProvider {
    private val deviceTag = MutableStateFlow(device)
    private var override: LocaleTag? = null
    private val state = MutableStateFlow(device)

    /** The effective locale. */
    override val locale: StateFlow<LocaleTag> = state.asStateFlow()

    /** Overrides the device locale; null follows it again. */
    override fun setAppLocale(tag: LocaleTag?) {
        override = tag
        state.value = tag ?: deviceTag.value
    }

    /** Simulates the user changing the device language. */
    public fun setDeviceLocale(tag: LocaleTag) {
        deviceTag.value = tag
        state.value = override ?: tag
    }
}

/** A fixed time zone a test can change. */
public class FixedTimeZoneProvider(id: String = "UTC") : TimeZoneProvider {
    private val state = MutableStateFlow(id)

    /** The time zone last [set]. */
    override val timeZoneId: StateFlow<String> = state.asStateFlow()

    /** Changes the time zone. */
    public fun set(id: String) {
        state.value = id
    }
}

/** An app lifecycle a test moves by hand. */
public class FakeAppLifecycle(initial: AppPhase = AppPhase.Foreground) : AppLifecycle {
    private val state = MutableStateFlow(initial)

    /** The phase last [set]. */
    override val phase: StateFlow<AppPhase> = state.asStateFlow()

    /** Moves to [phase]. */
    public fun set(phase: AppPhase) {
        state.value = phase
    }
}

/** A fixed client identity for tests. */
public fun testClientInfo(): ClientInfo = ClientInfo(
    appId = "test.app",
    appVersion = "1.0.0",
    build = "1",
    platform = ClientPlatform.Desktop,
    osName = "TestOS",
    osVersion = "1",
    deviceModel = null,
    sdkVersion = "0.0.0-test",
)

/**
 * A test environment: every module at [baseUrl] (default `https://api.test/`, a reserved name
 * that never resolves), [Posture.Live] unless told otherwise.
 */
public fun testEnvironment(
    baseUrl: String = "https://api.test/",
    posture: Posture = Posture.Live,
    endpoints: Map<String, ServiceEndpoint> = emptyMap(),
): StapelEnvironment = StapelEnvironment(
    name = "test",
    posture = posture,
    endpoints = endpoints,
    defaultEndpoint = ServiceEndpoint(baseUrl),
    client = testClientInfo(),
)
