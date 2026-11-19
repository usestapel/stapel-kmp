package stapel.conventions

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * The `stapel { }` block of a module build file.
 *
 * ```kotlin
 * plugins { id("stapel.substrate") }
 * stapel { description = "Result and error types for Stapel clients." }
 * ```
 */
abstract class StapelExtension {
    /** Set by the family plugin; a module build file does not change it. */
    abstract val family: Property<StapelFamily>

    /** Brick key for `bricks/<brick>/…` modules; absent otherwise. */
    abstract val brick: Property<String>

    /** POM description and first line of the module's docs. Required when published. */
    abstract val description: Property<String>

    /** Extra opt-in annotations (fully-qualified names) for this module's compilations. */
    abstract val optIns: ListProperty<String>

    /** Why this UI module has platform source sets (D2); empty = commonMain only. */
    abstract val platformSubjectReason: Property<String>

    /**
     * Regexes of packages held by the generated-code ratchet instead of the Dokka zero-undocumented
     * gate (ruling R11). Only generated packages belong here; the count is CODEGEN's gate.
     */
    abstract val ratchetedPackages: ListProperty<String>

    /** Adds opt-in annotations to every compilation of this module. */
    fun optIn(vararg annotationFqNames: String) {
        optIns.addAll(*annotationFqNames)
    }

    /** Declares that a platform API is this UI module's subject, allowing `src/{android,ios,jvm}Main`. */
    fun platformSubject(reason: String) {
        platformSubjectReason.set(reason)
    }
}
