package stapel.conventions

/** Module family; set by the family plugin, read by every gate. */
enum class StapelFamily(val published: Boolean, val compose: Boolean) {
    /** Pure Kotlin substrate artifact (`substrate/stapel-<x>`, `stapel.substrate`). */
    SUBSTRATE(published = true, compose = false),

    /** Compose substrate artifact (`stapel.substrate-ui`). */
    SUBSTRATE_UI(published = true, compose = true),

    /** Optional backend-core capability, pure (`capabilities/stapel-<x>`, `stapel.capability`). */
    CAPABILITY(published = true, compose = false),

    /** Compose half of a capability (`stapel.capability-ui`). */
    CAPABILITY_UI(published = true, compose = true),

    /** Brick domain library `stapel-<brick>` (`bricks/<brick>/domain`). */
    BRICK_DOMAIN(published = true, compose = false),

    /** Brick UI library `stapel-<brick>-ui` (`bricks/<brick>/ui`). */
    BRICK_UI(published = true, compose = true),

    /** Optional platform adapter of a brick `stapel-<brick>-<x>` (`bricks/<brick>/adapters/<x>`). */
    BRICK_ADAPTER(published = true, compose = false),

    /** Demos of a brick's UI (`bricks/<brick>/demo`), never published. */
    DEMO(published = false, compose = true),

    /** End-to-end tests of a brick against a real backend (`bricks/<brick>/e2e`), never published. */
    E2E(published = false, compose = false),

    /** JVM tool under `tools/` (generators); not published in v0. */
    TOOL(published = false, compose = false),

    /** Showcase gallery and apps under `showcase/`. */
    SHOWCASE(published = false, compose = true),

    /** `stapel-bom`. */
    BOM(published = true, compose = false),
}

/** Families whose main classpath must stay free of Compose, navigation and Koin-compose (D6). */
internal val PURE_FAMILIES = setOf(
    StapelFamily.SUBSTRATE,
    StapelFamily.CAPABILITY,
    StapelFamily.BRICK_DOMAIN,
    StapelFamily.BRICK_ADAPTER,
)

/** Kotlin multiplatform library families (the four-target set of D2). */
internal val LIBRARY_FAMILIES = setOf(
    StapelFamily.SUBSTRATE,
    StapelFamily.SUBSTRATE_UI,
    StapelFamily.CAPABILITY,
    StapelFamily.CAPABILITY_UI,
    StapelFamily.BRICK_DOMAIN,
    StapelFamily.BRICK_UI,
    StapelFamily.BRICK_ADAPTER,
)
