package stapel.result

/**
 * Outcome of an operation: a value of type [T] or an error of type [E].
 *
 * Generated operations return `StapelResult<T, <Op>Error>`, where `<Op>Error` is either a sealed
 * type holding exactly the codes bound to the operation plus the shared transport family
 * ([TransportFailure], reached through a [TransportCarrier] variant), or a typealias of the
 * brick-wide error type. Hand-written substrate code that has no domain table uses
 * `StapelResult<T, StapelError>`.
 *
 * Cancellation is never a value: a cancelled call throws `CancellationException`, it does not
 * return [Err].
 */
public sealed interface StapelResult<out T, out E : StapelError> {
    /**
     * The operation succeeded.
     *
     * @property value the result.
     */
    public data class Ok<out T>(public val value: T) : StapelResult<T, Nothing>

    /**
     * The operation failed.
     *
     * @property error the failure.
     */
    public data class Err<out E : StapelError>(public val error: E) : StapelResult<Nothing, E>
}

/** True when this is [StapelResult.Ok]. */
public val StapelResult<*, *>.isOk: Boolean get() = this is StapelResult.Ok

/** True when this is [StapelResult.Err]. */
public val StapelResult<*, *>.isErr: Boolean get() = this is StapelResult.Err

/** Transforms the value of an [StapelResult.Ok]; an error passes through unchanged. */
public inline fun <T, E : StapelError, R> StapelResult<T, E>.map(transform: (T) -> R): StapelResult<R, E> =
    when (this) {
        is StapelResult.Ok -> StapelResult.Ok(transform(value))
        is StapelResult.Err -> this
    }

/** Transforms the error of an [StapelResult.Err]; a value passes through unchanged. */
public inline fun <T, E : StapelError, F : StapelError> StapelResult<T, E>.mapError(
    transform: (E) -> F,
): StapelResult<T, F> =
    when (this) {
        is StapelResult.Ok -> this
        is StapelResult.Err -> StapelResult.Err(transform(error))
    }

/** Chains a second operation that runs only when this one succeeded. */
public inline fun <T, E : StapelError, R> StapelResult<T, E>.flatMap(
    transform: (T) -> StapelResult<R, E>,
): StapelResult<R, E> =
    when (this) {
        is StapelResult.Ok -> transform(value)
        is StapelResult.Err -> this
    }

/** Collapses both arms into one value. */
public inline fun <T, E : StapelError, R> StapelResult<T, E>.fold(onOk: (T) -> R, onErr: (E) -> R): R =
    when (this) {
        is StapelResult.Ok -> onOk(value)
        is StapelResult.Err -> onErr(error)
    }

/** Runs [action] on the value when this succeeded; returns this unchanged. */
public inline fun <T, E : StapelError> StapelResult<T, E>.onOk(action: (T) -> Unit): StapelResult<T, E> {
    if (this is StapelResult.Ok) action(value)
    return this
}

/** Runs [action] on the error when this failed; returns this unchanged. */
public inline fun <T, E : StapelError> StapelResult<T, E>.onErr(action: (E) -> Unit): StapelResult<T, E> {
    if (this is StapelResult.Err) action(error)
    return this
}

/** The value, or null when this failed. */
public fun <T> StapelResult<T, *>.getOrNull(): T? = (this as? StapelResult.Ok)?.value

/** The error, or null when this succeeded. */
public fun <E : StapelError> StapelResult<*, E>.errorOrNull(): E? = (this as? StapelResult.Err)?.error

/** The value, or what [fallback] computes from the error. */
public inline fun <T, E : StapelError> StapelResult<T, E>.getOrElse(fallback: (E) -> T): T =
    when (this) {
        is StapelResult.Ok -> value
        is StapelResult.Err -> fallback(error)
    }

/**
 * The value, or a thrown [StapelException] carrying the error.
 *
 * For call sites that prefer exceptions (tests, scripts). Product code matches on the result.
 */
public fun <T> StapelResult<T, *>.getOrThrow(): T =
    when (this) {
        is StapelResult.Ok -> value
        is StapelResult.Err -> throw StapelException(error)
    }

/**
 * The exception [getOrThrow] throws.
 *
 * @property error the failure it carries.
 */
public class StapelException(public val error: StapelError) : RuntimeException(error.code.value)
