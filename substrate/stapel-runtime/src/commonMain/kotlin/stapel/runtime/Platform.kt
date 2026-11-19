package stapel.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The platform handle the app creates once and hands to every factory that needs the OS.
 *
 * Android: wraps the `Application` (`PlatformContext(application)`); iOS and desktop:
 * `PlatformContext()`. Libraries never create one implicitly.
 */
public interface PlatformContext

/** The dispatchers substrate code switches to; injected, never read from `Dispatchers` directly. */
public interface StapelDispatchers {
    /** The UI thread, immediate when already on it. Desktop apps need `kotlinx-coroutines-swing`. */
    public val main: CoroutineDispatcher

    /** CPU-bound work. */
    public val default: CoroutineDispatcher

    /** Blocking I/O (Keychain, Keystore, files); equals [default] where the platform has no I/O pool. */
    public val io: CoroutineDispatcher
}

/** The platform's dispatchers. [StapelDispatchers.main] is resolved on first use. */
public fun defaultDispatchers(): StapelDispatchers = DefaultDispatchers

private object DefaultDispatchers : StapelDispatchers {
    override val main: CoroutineDispatcher get() = Dispatchers.Main.immediate
    override val default: CoroutineDispatcher get() = Dispatchers.Default
    override val io: CoroutineDispatcher get() = platformIoDispatcher()
}

internal expect fun platformIoDispatcher(): CoroutineDispatcher

/** The wall clock. The only place in the substrate that reads `Clock.System`; everything else injects a [Clock]. */
public fun systemClock(): Clock = Clock.System

/** Source of unique ids (request ids, installation ids). */
public fun interface IdGenerator {
    /** A new random UUID in canonical lower-case text form. */
    public fun uuid(): String
}

/** Random version-4 UUIDs from the platform's secure random source. */
@OptIn(ExperimentalUuidApi::class)
public fun randomIdGenerator(): IdGenerator = IdGenerator { Uuid.random().toString() }
