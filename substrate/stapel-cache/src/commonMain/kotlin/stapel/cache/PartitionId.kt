package stapel.cache

/**
 * Name of a storage partition: [App] for public data that survives logout, or one partition per
 * account. An account partition is named `u-` plus the first 16 hex digits of the SHA-256 of the
 * account id, so an account id never appears in a file path.
 *
 * @property name the directory-safe partition name (`app` or `u-<16 hex>`).
 */
public class PartitionId private constructor(public val name: String) {
    /** `true` for an account partition. */
    public val isAccount: Boolean get() = name != APP_NAME

    /** Equal when the names are equal. */
    override fun equals(other: Any?): Boolean = other is PartitionId && other.name == name

    /** Hash of [name]. */
    override fun hashCode(): Int = name.hashCode()

    /** Debug form `PartitionId(<name>)`. */
    override fun toString(): String = "PartitionId($name)"

    /** Constructors of partition names. */
    public companion object {
        private const val APP_NAME = "app"
        private val ACCOUNT_NAME = Regex("u-[0-9a-f]{16}")

        /** The application partition: public data, kept on logout. */
        public val App: PartitionId = PartitionId(APP_NAME)

        /** The partition of [accountId]. */
        public fun account(accountId: String): PartitionId {
            require(accountId.isNotEmpty()) { "accountId must not be empty" }
            return PartitionId("u-" + Sha256.hex(accountId.encodeToByteArray()).take(16))
        }

        /** Parses a name produced by [name]; `null` for anything else (foreign directories are ignored). */
        public fun parse(name: String): PartitionId? = when {
            name == APP_NAME -> App
            ACCOUNT_NAME.matches(name) -> PartitionId(name)
            else -> null
        }
    }
}
