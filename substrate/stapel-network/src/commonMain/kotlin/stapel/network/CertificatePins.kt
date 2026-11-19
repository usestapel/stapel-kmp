package stapel.network

/**
 * Certificate pins the app supplies (the library ships none). A pin is `sha256/<base64 SPKI hash>`.
 * Every pinned host needs at least two pins (current + backup), or [Builder.build] throws: a single
 * pin turns the next certificate rotation into an outage.
 */
public class CertificatePins private constructor(
    /** Host pattern (`api.example.com`, `*.example.com`, `**.example.com`) → pins. */
    public val byHost: Map<String, Set<String>>,
) {
    /** Collects pins per host pattern. */
    public class Builder {
        private val pins = linkedMapOf<String, MutableSet<String>>()

        /** Adds [spkiSha256] pins (`sha256/…`) for [hostPattern]. */
        public fun pin(hostPattern: String, vararg spkiSha256: String): Builder {
            require(hostPattern.isNotBlank()) { "host pattern is blank" }
            for (pin in spkiSha256) {
                require(pin.startsWith("sha256/") && pin.length > "sha256/".length) {
                    "pin for $hostPattern must look like sha256/<base64>, got '$pin'"
                }
            }
            pins.getOrPut(hostPattern) { linkedSetOf() }.addAll(spkiSha256)
            return this
        }

        /** The immutable pin set. */
        public fun build(): CertificatePins {
            for ((host, set) in pins) {
                require(set.size >= 2) { "host $host has ${set.size} pin(s); at least two (current + backup) are required" }
            }
            return CertificatePins(pins.mapValues { it.value.toSet() })
        }
    }

    /** True when no host is pinned. */
    public val isEmpty: Boolean get() = byHost.isEmpty()

    /** Holders of the empty set. */
    public companion object {
        /** No pinning (the platform trust store alone decides). */
        public val None: CertificatePins = CertificatePins(emptyMap())
    }
}
