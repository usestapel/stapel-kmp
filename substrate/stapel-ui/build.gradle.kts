// stapel-ui: the paradigm-neutral UI substrate — theme from stapel-tokens, layout (size classes, the
// page gutter), the 20-slot StapelSkin seam with a Material-free FoundationSkin, wrappers and composites.
// No Material on this classpath (R9): brick UI libraries compile against this module only.
plugins {
    id("stapel.substrate-ui")
}

stapel {
    description = "Stapel UI substrate: theme, layout, the StapelSkin slot seam, foundation skin, base components."
    platformSubject("input kind (touch vs pointer) and the platform touch-target floor differ per platform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelTokens)
            api(projects.stapelI18n)
            api(projects.stapelResult)
            api(projects.stapelMvi)
            api(projects.stapelImage)
            implementation(libs.kotlinx.coroutines.core)
        }
        getByName("skikoTest").dependencies {
            implementation(projects.showcase.skinContract)
        }
    }
}
