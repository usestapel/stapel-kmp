package stapel.conventions

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertContains

/**
 * Inversion tests: every gate is proven able to fail on a fixture that breaks its rule, with the
 * message a brigade will see. One conforming fixture proves the gates are not red by default.
 */
class GatesFunctionalTest {

    @Test
    fun `conforming layout passes the direction gate`() {
        FixtureBuild("conforming")
            .module("substrate/stapel-result", substrate())
            .module("substrate/stapel-runtime", substrate("api(projects.stapelResult)"))
            .module("substrate/stapel-ui", substrate("api(projects.stapelResult)", plugin = "stapel.substrate-ui"))
            .module("bricks/auth/domain", substrate("api(projects.stapelRuntime)", plugin = "stapel.brick-domain"))
            .module("bricks/auth/ui", substrate("implementation(projects.stapelUi)", plugin = "stapel.brick-ui"))
            .succeeds("checkDependencyDirection", "checkPackageRoot", "checkKoinLeak", "checkWasmReady", "checkCatalog")
    }

    @Test
    fun `every family plugin applies and compiles its jvm target`() {
        val fx = FixtureBuild("families")
            .module("substrate/stapel-result", substrate())
            .module("substrate/stapel-ui", substrate(plugin = "stapel.substrate-ui"))
            .module("capabilities/stapel-captcha", substrate("api(projects.stapelResult)", plugin = "stapel.capability"))
            .module("capabilities/stapel-captcha-ui", substrate("api(projects.stapelCaptcha)", plugin = "stapel.capability-ui"))
            .module("bricks/auth/domain", substrate("api(projects.stapelResult)", plugin = "stapel.brick-domain"))
            .module("bricks/auth/ui", substrate(plugin = "stapel.brick-ui"))
            .module("bricks/auth/adapters/biometric", substrate(plugin = "stapel.brick-adapter"))
            .module("bricks/auth/demo", "plugins { id(\"stapel.brick-demo\") }")
            .module("bricks/auth/e2e", "plugins { id(\"stapel.brick-e2e\") }\ndependencies { testImplementation(projects.stapelAuth) }")
            .module("tools/codegen/ir", "plugins { id(\"stapel.jvm-tool\") }")
            .module("showcase/gallery", "plugins { id(\"stapel.showcase\") }")
            .module("bom", "plugins { id(\"stapel.bom\") }")
        listOf(
            "bricks/auth/domain/src/commonMain/kotlin/stapel/auth/A.kt" to "package stapel.auth\n/** A. */\npublic class A",
            "bricks/auth/ui/src/commonMain/kotlin/stapel/auth/ui/B.kt" to "package stapel.auth.ui\n/** B. */\npublic fun b(): stapel.auth.A = stapel.auth.A()",
            "bricks/auth/demo/src/commonMain/kotlin/stapel/auth/ui/demo/D.kt" to "package stapel.auth.ui.demo\nfun d() = stapel.auth.ui.b()",
            "bricks/auth/e2e/src/test/kotlin/stapel/auth/e2e/E.kt" to "package stapel.auth.e2e\nclass E { @org.junit.jupiter.api.Test fun e() { stapel.auth.A() } }",
            "tools/codegen/ir/src/main/kotlin/stapel/tools/codegen/ir/Ir.kt" to "package stapel.tools.codegen.ir\nfun ir() = 1",
            "showcase/gallery/src/commonMain/kotlin/stapel/showcase/G.kt" to "package stapel.showcase\nfun g() = 1",
        ).forEach { (path, text) -> fx.file(path, text) }
        val r = fx.succeeds(
            "compileKotlinJvm", ":tools:codegen:ir:compileKotlin", ":stapel-auth-e2e:compileTestKotlin",
            "checkDependencyDirection", "checkPackageRoot", ":stapel-auth-e2e:test",
            ":stapel-bom:generatePomFileForMavenPublication",
        )
        assertContains(r.output, ":stapel-auth-e2e:test SKIPPED")
        val bomPom = fx.dir.resolve("bom/build/publications/maven/pom-default.xml").readText()
        assertContains(bomPom, "<artifactId>stapel-auth-ui</artifactId>")
        kotlin.test.assertFalse(bomPom.contains("stapel-auth-ui-demo"), "demo must not be in the BOM")
    }

    @Test
    fun `brick may not depend on another brick`() {
        val r = FixtureBuild("brick-to-brick")
            .module("substrate/stapel-result", substrate())
            .module("bricks/chat/domain", substrate(plugin = "stapel.brick-domain"))
            .module("bricks/auth/domain", substrate("api(projects.stapelChat)", plugin = "stapel.brick-domain"))
            .fails(":stapel-auth:checkDependencyDirection")
        assertContains(r.output, "stapel-auth (BRICK_DOMAIN) -> stapel-chat (BRICK_DOMAIN)")
        assertContains(r.output, "declare the interface in the substrate")
    }

    @Test
    fun `brick ui may not depend on another brick's domain`() {
        val r = FixtureBuild("ui-to-other-brick")
            .module("bricks/chat/domain", substrate(plugin = "stapel.brick-domain"))
            .module("bricks/auth/domain", substrate(plugin = "stapel.brick-domain"))
            .module("bricks/auth/ui", substrate("implementation(projects.stapelChat)", plugin = "stapel.brick-ui"))
            .fails(":stapel-auth-ui:checkDependencyDirection")
        assertContains(r.output, "a brick never depends on another brick")
    }

    @Test
    fun `domain may not depend on a ui module`() {
        val r = FixtureBuild("domain-to-ui")
            .module("substrate/stapel-ui", substrate(plugin = "stapel.substrate-ui"))
            .module("bricks/auth/domain", substrate("api(projects.stapelUi)", plugin = "stapel.brick-domain"))
            .fails(":stapel-auth:checkDependencyDirection")
        assertContains(r.output, "stapel-auth (BRICK_DOMAIN) -> stapel-ui (SUBSTRATE_UI)")
    }

    @Test
    fun `compose arriving through a substrate api edge is caught on the domain`() {
        val r = FixtureBuild("transitive-compose")
            .module("substrate/stapel-network", substrate("api(libs.compose.runtime)"))
            .module("bricks/auth/domain", substrate("api(projects.stapelNetwork)", plugin = "stapel.brick-domain"))
            .fails(":stapel-auth:checkDependencyDirection")
        assertContains(r.output, "stapel-auth: org.jetbrains.compose.runtime:runtime")
        assertContains(r.output, "D6: domain and pure substrate libraries have no Compose")
    }

    @Test
    fun `substrate edges go strictly downward by rank`() {
        val r = FixtureBuild("rank-inversion")
            .module("substrate/stapel-network", substrate())
            .module("substrate/stapel-result", substrate("api(projects.stapelNetwork)"))
            .fails(":stapel-result:checkDependencyDirection")
        assertContains(r.output, "stapel-result (rank 0) -> stapel-network (rank 3)")
    }

    @Test
    fun `room outside a room artifact is refused`() {
        val r = FixtureBuild("room-outside")
            .module("substrate/stapel-cache", substrate("implementation(libs.room3.runtime)"))
            .fails(":stapel-cache:checkDependencyDirection")
        assertContains(r.output, "androidx.room3:room3-runtime")
        assertContains(r.output, "Room/SQLite only in `*-room` artifacts")
    }

    @Test
    fun `material is refused in a brick ui`() {
        val r = FixtureBuild("material-in-brick-ui")
            .module("bricks/auth/domain", substrate(plugin = "stapel.brick-domain"))
            .module("bricks/auth/ui", substrate("implementation(libs.compose.material3)", plugin = "stapel.brick-ui"))
            .fails(":stapel-auth-ui:checkDependencyDirection")
        assertContains(r.output, "R9: Material lives only in the default skin")
    }

    @Test
    fun `a pre-release coordinate not on the allow-list is refused`() {
        val r = FixtureBuild("prerelease")
            .module("substrate/stapel-result", substrate("implementation(\"org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0-rc02\")"))
            .fails(":stapel-result:checkDependencyDirection")
        assertContains(r.output, "pre-release org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0-rc02")
    }

    @Test
    fun `koin outside the di package is refused`() {
        val fx = FixtureBuild("koin-leak")
            .module("substrate/stapel-network", substrate("implementation(libs.koin.core)"))
            .file(
                "substrate/stapel-network/src/commonMain/kotlin/stapel/network/di/NetworkModule.kt",
                "package stapel.network.di\nimport org.koin.dsl.module\n",
            )
        fx.succeeds(":stapel-network:checkKoinLeak")
        fx.file(
            "substrate/stapel-network/src/commonMain/kotlin/stapel/network/Client.kt",
            "package stapel.network\nimport org.koin.core.Koin\n",
        )
        val r = fx.fails(":stapel-network:checkKoinLeak")
        assertContains(r.output, "org.koin is allowed only in package 'stapel.network.di'")
        assertContains(r.output, "src/commonMain/kotlin/stapel/network/Client.kt")
    }

    @Test
    fun `the di module itself may name koin in its own package but not outside it`() {
        val fx = FixtureBuild("koin-di-module")
            .module("substrate/stapel-di", substrate("api(libs.koin.core)"))
            .file(
                "substrate/stapel-di/src/commonMain/kotlin/stapel/di/StapelKoin.kt",
                "package stapel.di\nimport org.koin.core.KoinApplication\n",
            )
        fx.succeeds(":stapel-di:checkKoinLeak")
        fx.file(
            "substrate/stapel-di/src/commonMain/kotlin/stapel/Elsewhere.kt",
            "package stapel\nimport org.koin.core.Koin\n",
        )
        val r = fx.fails(":stapel-di:checkKoinLeak")
        assertContains(r.output, "org.koin is allowed only in package 'stapel.di'")
    }

    @Test
    fun `a commonMain dependency without a wasm variant is refused in a pure library`() {
        val r = FixtureBuild("wasm")
            .module("substrate/stapel-network", substrate("implementation(libs.ktor.client.okhttp)"))
            .fails(":stapel-network:checkWasmReady")
        assertContains(r.output, "Not in gradle/wasm-ready.txt: io.ktor:ktor-client-okhttp")
    }

    @Test
    fun `a file outside the derived package is refused`() {
        val r = FixtureBuild("package-root")
            .module("substrate/stapel-result", substrate())
            .file("substrate/stapel-result/src/commonMain/kotlin/Foo.kt", "package io.example.result\n")
            .fails(":stapel-result:checkPackageRoot")
        assertContains(r.output, "every file must be in package 'stapel.result'")
    }

    @Test
    fun `a ui library with platform sources needs a declared subject`() {
        val fx = FixtureBuild("source-sets")
            .module("substrate/stapel-ui", substrate(plugin = "stapel.substrate-ui"))
            .file("substrate/stapel-ui/src/androidMain/kotlin/stapel/ui/A.kt", "package stapel.ui\n")
        val r = fx.fails(":stapel-ui:checkSourceSets")
        assertContains(r.output, "UI libraries are commonMain-only (D2)")
    }

    @Test
    fun `a pre-release version in the catalog is refused`() {
        val fx = FixtureBuild("catalog")
        val toml = fx.dir.resolve("gradle/libs.versions.toml")
        toml.writeText(toml.readText().replace("datetime = \"0.8.0\"", "datetime = \"0.8.0-rc02\""))
        val r = fx.fails("checkCatalog")
        assertContains(r.output, "version 'datetime' = '0.8.0-rc02' is a pre-release")
    }

    @Test
    fun `zero executed tests on a target fails verifyTestsRan`() {
        val r = FixtureBuild("tests-ran")
            .module(
                "substrate/stapel-result",
                substrate() + "\ntasks.named<Test>(\"jvmTest\") { filter { excludeTestsMatching(\"*\"); isFailOnNoMatchingTests = false } }\n",
            )
            .file(
                "substrate/stapel-result/src/commonMain/kotlin/stapel/result/R.kt",
                "package stapel.result\n/** One. */\npublic fun one(): Int = 1\n",
            )
            .file(
                "substrate/stapel-result/src/commonTest/kotlin/stapel/result/RTest.kt",
                "package stapel.result\nimport kotlin.test.*\nclass RTest { @Test fun returnsOne() { assertEquals(1, one()) } }\n",
            )
            .fails(":stapel-result:jvmTest")
        assertContains(r.output, "verifyTestsRan: 'jvmTest' executed 0 tests")
    }

    @Test
    fun `an undocumented public symbol fails the dokka gate`() {
        assumeTrue(System.getProperty("os.name").lowercase().contains("mac"), "Dokka resolves iOS source sets on macOS only")
        val fx = FixtureBuild("dokka")
            .module("substrate/stapel-result", substrate())
            .file(
                "substrate/stapel-result/src/commonMain/kotlin/stapel/result/R.kt",
                "package stapel.result\n/** Documented. */\npublic fun documented(): Int = 1\n",
            )
        fx.succeeds(":stapel-result:dokkaGeneratePublicationHtml")
        fx.file(
            "substrate/stapel-result/src/commonMain/kotlin/stapel/result/U.kt",
            "package stapel.result\npublic fun undocumented(): Int = 2\n",
        )
        val r = fx.fails(":stapel-result:dokkaGeneratePublicationHtml")
        assertContains(r.output, "undocumented")
    }
}
