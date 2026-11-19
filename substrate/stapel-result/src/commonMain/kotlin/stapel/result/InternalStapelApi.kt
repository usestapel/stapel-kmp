package stapel.result

/**
 * Marks a declaration that is `public` only so that other stapel-kmp artifacts can reach it.
 *
 * It is not part of the supported API: it may change or disappear in any release, it is excluded
 * from the ABI dumps and from the published documentation. Opt in with
 * `@OptIn(InternalStapelApi::class)` only inside stapel-kmp itself.
 */
@RequiresOptIn(
    message = "Internal stapel-kmp API: not covered by compatibility guarantees.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
@MustBeDocumented
public annotation class InternalStapelApi
