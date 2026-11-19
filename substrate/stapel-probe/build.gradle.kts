// BUILD PROBE — not a product module. Proves the `stapel.substrate` convention on every target
// (jvm, android host, iosArm64, iosSimulatorArm64), the gates and the publishing path. Delete it once
// real substrate modules exist (the build owner removes it; see docs/adding-a-module.md).
plugins {
    id("stapel.substrate")
}

stapel {
    description = "Build probe for the stapel.substrate convention (not a product module)."
}
