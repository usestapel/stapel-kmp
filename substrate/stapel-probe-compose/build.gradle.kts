// BUILD PROBE — not a product module. Proves the `stapel.substrate-ui` convention: Compose
// Multiplatform on every target, Compose UI tests on the skiko targets (desktop headless + iOS
// simulator). Delete it once real UI modules exist.
plugins {
    id("stapel.substrate-ui")
}

stapel {
    description = "Build probe for the stapel.substrate-ui convention (not a product module)."
}
