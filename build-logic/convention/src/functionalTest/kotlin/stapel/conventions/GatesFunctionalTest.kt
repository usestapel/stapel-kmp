package stapel.conventions

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

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
    fun `a consumer may depend on any brick's ui module`() {
        FixtureBuild("consumer-to-brick-ui")
            .module("bricks/auth/domain", substrate(plugin = "stapel.brick-domain"))
            .module("bricks/auth/ui", substrate(plugin = "stapel.brick-ui"))
            .module("bricks/chat/domain", substrate(plugin = "stapel.brick-domain"))
            .module("bricks/chat/ui", substrate(plugin = "stapel.brick-ui"))
            .module("bricks/chat/demo", "plugins { id(\"stapel.brick-demo\") }\nkotlin { sourceSets { commonMain.dependencies { implementation(projects.stapelAuthUi) } } }")
            .module(
                "showcase/stories",
                "plugins { id(\"stapel.showcase\") }\nkotlin { sourceSets { commonMain.dependencies { implementation(projects.stapelAuthUi); implementation(projects.stapelChatUi) } } }",
            )
            .succeeds(":showcase:stories:checkDependencyDirection", ":stapel-chat-ui-demo:checkDependencyDirection")
    }

    @Test
    fun `a feature module may not depend on a consumer module`() {
        val r = FixtureBuild("feature-to-consumer")
            .module("bricks/auth/domain", substrate(plugin = "stapel.brick-domain"))
            .module("showcase/stories", "plugins { id(\"stapel.showcase\") }")
            .module("bricks/auth/ui", substrate("implementation(projects.showcase.stories)", plugin = "stapel.brick-ui"))
            .fails(":stapel-auth-ui:checkDependencyDirection")
        assertContains(r.output, "a feature module never depends on a consumer module")
    }

    @Test
    fun `an Xcode project or app build above the iOS floor fails checkIosFloor`() {
        val fx = FixtureBuild("ios-floor")
            .module("substrate/stapel-result", substrate())
            .file("samples/app/iosApp/iosApp.xcodeproj/project.pbxproj", "IPHONEOS_DEPLOYMENT_TARGET = 15.0;")
            .file(
                "samples/app/shared/build.gradle.kts",
                "val IOS_FLOOR = \"15.0\"\nkotlin { iosArm64 { binaries.framework { } } }\n" +
                    "// \"-Xoverride-konan-properties=osVersionMin.ios_arm64=\$IOS_FLOOR;osVersionMin.ios_simulator_arm64=\$IOS_FLOOR\"",
            )
        fx.succeeds("checkIosFloor")
        fx.file("samples/app/iosApp/iosApp.xcodeproj/project.pbxproj", "IPHONEOS_DEPLOYMENT_TARGET = 18.2;")
            .file("samples/app/shared/build.gradle.kts", "kotlin { iosArm64 { binaries.framework { } } }")
        val r = fx.fails("stapelCheckFast")
        assertContains(r.output, "samples/app/iosApp/iosApp.xcodeproj/project.pbxproj: IPHONEOS_DEPLOYMENT_TARGET = 18.2 (must be 15.0)")
        assertContains(r.output, "samples/app/shared/build.gradle.kts links an Apple binary but does not pin osVersionMin.ios_arm64")
    }

    @Test
    fun `a consumer listed in the BOM fails checkBomMembers`() {
        val r = FixtureBuild("bom-consumer")
            .module("substrate/stapel-result", substrate())
            .module("showcase/stories", "plugins { id(\"stapel.showcase\") }")
            .module("bom", "plugins { id(\"stapel.bom\") }\ndependencies { constraints { api(projects.showcase.stories) } }")
            .fails(":stapel-bom:checkBomMembers")
        assertContains(r.output, ":showcase:stories (SHOWCASE) is a consumer module")
    }

    @Test
    fun `a brick test kit gets its own ui and may not reach another brick`() {
        val ok = FixtureBuild("brick-testing")
            .module("bricks/auth/domain", substrate(plugin = "stapel.brick-domain"))
            .module("bricks/auth/ui", substrate(plugin = "stapel.brick-ui"))
            .module("bricks/auth/testing", substrate(plugin = "stapel.brick-testing"))
        ok.file("bricks/auth/ui/src/commonMain/kotlin/stapel/auth/ui/T.kt", "package stapel.auth.ui\n/** T. */\npublic const val TAG: String = \"t\"")
        ok.file("bricks/auth/testing/src/commonMain/kotlin/stapel/auth/testing/K.kt", "package stapel.auth.testing\n/** K. */\npublic val k: String = stapel.auth.ui.TAG")
        ok.succeeds(":stapel-auth-testing:compileKotlinJvm", ":stapel-auth-testing:checkDependencyDirection")
        val r = FixtureBuild("brick-testing-other")
            .module("bricks/chat/domain", substrate(plugin = "stapel.brick-domain"))
            .module("bricks/chat/ui", substrate(plugin = "stapel.brick-ui"))
            .module("bricks/auth/domain", substrate(plugin = "stapel.brick-domain"))
            .module("bricks/auth/testing", substrate("api(projects.stapelChatUi)", plugin = "stapel.brick-testing"))
            .fails(":stapel-auth-testing:checkDependencyDirection")
        assertContains(r.output, "a brick never depends on another brick")
    }

    @Test
    fun `the published catalog and the registry list feature modules only`() {
        val fx = FixtureBuild("catalog-registry")
            .module("substrate/stapel-result", substrate())
            .module("bricks/auth/domain", substrate(plugin = "stapel.brick-domain"))
            .module("bricks/auth/ui", substrate(plugin = "stapel.brick-ui"))
            .module("bricks/auth/demo", "plugins { id(\"stapel.brick-demo\") }")
            .module("showcase/gallery", "plugins { id(\"stapel.showcase\") }")
            .module("catalog", "plugins { id(\"stapel.catalog\") }")
        fx.succeeds(":stapel-catalog:generateCatalogAsToml", ":stapel-catalog:checkPublishedCatalog", "exportKmpRegistry")
        val toml = fx.dir.resolve("catalog/build/version-catalog/libs.versions.toml").readText()
        assertContains(toml, "stapel-auth-ui = {group = \"io.github.usestapel\", name = \"stapel-auth-ui\"")
        kotlin.test.assertFalse("demo" in toml || "gallery" in toml, toml)
        val registry = fx.dir.resolve("kmp-registry.json").readText()
        assertContains(registry, "\"stapel-auth-ui\"")
        kotlin.test.assertFalse("demo" in registry || "gallery" in registry, registry)
        fx.succeeds("checkKmpRegistry")
        // Inversion: a stale registry fails its drift gate.
        fx.dir.resolve("kmp-registry.json").writeText(registry.replace("\"stapel-result\"", "\"stapel-gone\""))
        val r = fx.fails("checkKmpRegistry")
        assertContains(r.output, "kmp-registry.json is stale")
    }

    @Test
    fun `the registry runs after the manifests it reads when both are exported in one invocation`() {
        val fx = FixtureBuild("registry-order")
            .module("substrate/stapel-result", substrate())
            .module("bricks/auth/domain", substrate(plugin = "stapel.brick-domain"))
            .module("bricks/auth/ui", substrate(plugin = "stapel.brick-ui"))
        // The registry is named first on the command line; without the ordering rule Gradle runs it first.
        val order = fx.succeeds("exportKmpRegistry", ":stapel-auth-ui:exportNavManifest", "--dry-run").output
            .lines().filter { it.endsWith(" SKIPPED") }.map { it.removeSuffix(" SKIPPED") }
        val manifest = order.indexOf(":stapel-auth-ui:exportNavManifest")
        val registry = order.indexOf(":exportKmpRegistry")
        assertTrue(manifest in 0 until registry, order.joinToString("\n"))
    }

    @Test
    fun `a gradle plugin module bundles its tool and gets a group-derived id`() {
        val fx = FixtureBuild("gradle-plugin")
            .module("tools/demo-gen/core", "plugins { id(\"stapel.jvm-tool\") }")
            .module(
                "tools/demo-gen/gradle-plugin",
                """
                plugins { id("stapel.gradle-plugin") }
                stapel { description = "fixture"; pluginImplementationClass.set("stapel.tools.demo.P") }
                dependencies { stapelBundled(projects.tools.demoGen.core) }
                """,
            )
        fx.file("tools/demo-gen/core/src/main/kotlin/stapel/tools/demo/Core.kt", "package stapel.tools.demo\nobject Core { fun v() = 1 }")
        fx.file(
            "tools/demo-gen/gradle-plugin/src/main/kotlin/stapel/tools/demo/P.kt",
            "package stapel.tools.demo\nclass P : org.gradle.api.Plugin<org.gradle.api.Project> { override fun apply(p: org.gradle.api.Project) { Core.v() } }",
        )
        fx.succeeds(":tools:demo-gen:gradle-plugin:jar", ":tools:demo-gen:gradle-plugin:generatePomFileForPluginMavenPublication")
        val jar = java.util.zip.ZipFile(fx.dir.resolve("tools/demo-gen/gradle-plugin/build/libs").listFiles()!!.single { it.name.endsWith(".jar") && "sources" !in it.name && "javadoc" !in it.name })
        kotlin.test.assertNotNull(jar.getEntry("stapel/tools/demo/Core.class"), "the bundled tool class is in the plugin jar")
        kotlin.test.assertNotNull(jar.getEntry("META-INF/gradle-plugins/io.github.usestapel.demo.properties"))
        val pom = fx.dir.resolve("tools/demo-gen/gradle-plugin/build/publications/pluginMaven/pom-default.xml").readText()
        assertContains(pom, "<artifactId>stapel-demo-gradle-plugin</artifactId>")
        kotlin.test.assertFalse("<artifactId>core</artifactId>" in pom, "the unpublished tool is not a POM dependency")
    }

    @Test
    fun `a callee's signature change does not move a compose dependent's abi dump`() {
        val fx = FixtureBuild("compose-singletons")
            .module("substrate/stapel-ui", substrate(plugin = "stapel.substrate-ui"))
            .module("substrate/stapel-shell", substrate("api(projects.stapelUi)", plugin = "stapel.substrate-ui"))
        fx.file(
            "substrate/stapel-ui/src/commonMain/kotlin/stapel/ui/Leaf.kt",
            "package stapel.ui\n/** Leaf. */\n@androidx.compose.runtime.Composable public fun Leaf(x: Int) { androidx.compose.foundation.layout.Box { } }",
        )
        fx.file(
            "substrate/stapel-shell/src/commonMain/kotlin/stapel/shell/Host.kt",
            "package stapel.shell\n/** Host. */\n@androidx.compose.runtime.Composable public fun Host(content: @androidx.compose.runtime.Composable () -> Unit = { stapel.ui.Leaf(1) }) { content() }",
        )
        fx.succeeds(":stapel-ui:updateKotlinAbi", ":stapel-shell:updateKotlinAbi")
        val dump = fx.dir.resolve("substrate/stapel-shell/api/jvm/stapel-shell.api").readText()
        kotlin.test.assertFalse("ComposableSingletons" in dump, dump)
        assertContains(dump, "Host")
        // The callee changes shape; the dependent's own source does not change.
        fx.file(
            "substrate/stapel-ui/src/commonMain/kotlin/stapel/ui/Leaf.kt",
            "package stapel.ui\n/** Leaf. */\n@androidx.compose.runtime.Composable public fun Leaf(x: Int, y: String = \"\") { androidx.compose.foundation.layout.Box { } }",
        )
        fx.succeeds(":stapel-ui:updateKotlinAbi", ":stapel-shell:checkKotlinAbi")
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

    @Test
    fun `a compose module resolves one lifecycle line and an iosMain compiles`() {
        val fx = FixtureBuild("library-lines")
            .module("substrate/stapel-ui", substrate(plugin = "stapel.substrate-ui"))
            .file("substrate/stapel-ui/src/commonMain/kotlin/stapel/ui/A.kt", "package stapel.ui\n/** A. */\npublic expect val a: Int")
            .file("substrate/stapel-ui/src/iosMain/kotlin/stapel/ui/A.ios.kt", "package stapel.ui\n/** A. */\npublic actual val a: Int = 1")
            .file("substrate/stapel-ui/src/jvmMain/kotlin/stapel/ui/A.jvm.kt", "package stapel.ui\n/** A. */\npublic actual val a: Int = 2")
            .file("substrate/stapel-ui/src/androidMain/kotlin/stapel/ui/A.android.kt", "package stapel.ui\n/** A. */\npublic actual val a: Int = 3")
        fx.file("substrate/stapel-ui/build.gradle.kts", """
            plugins { id("stapel.substrate-ui") }
            stapel { description = "fixture"; platformSubject("fixture") }
        """)
        val tasks = mutableListOf(":stapel-ui:checkLibraryLines")
        // Duplicate klibs show up in the shared native metadata compilation (macOS only).
        if (System.getProperty("os.name").lowercase().contains("mac")) tasks += ":stapel-ui:compileIosMainKotlinMetadata"
        fx.succeeds(*tasks.toTypedArray())
    }

    @Test
    fun `a JetBrains module next to its androidx twin fails checkLibraryLines`() {
        val r = FixtureBuild("library-twins")
            .module(
                "substrate/stapel-ui",
                substrate(
                    "implementation(libs.jb.navigation3.ui); implementation(\"androidx.navigation3:navigation3-ui:1.2.0\")",
                    plugin = "stapel.substrate-ui",
                ),
            )
            .fails(":stapel-ui:checkLibraryLines")
        assertContains(r.output, "org.jetbrains.androidx.navigation3:navigation3-ui:1.2.0 next to androidx.navigation3:navigation3-ui:1.2.0")
        assertContains(r.output, "REDIRECT_SHIMS")
    }

    @Test
    fun `ambient sources and swallowed cancellation fail checkSourceRules outside stapel-runtime`() {
        val ambient = "package PKG\n/** A. */\npublic fun a(): Any = kotlinx.coroutines.Dispatchers.Default\n"
        val fx = FixtureBuild("source-rules")
            .module("substrate/stapel-runtime", substrate("implementation(libs.kotlinx.coroutines.core)"))
            .module("substrate/stapel-result", substrate("implementation(libs.kotlinx.coroutines.core)"))
            .file("substrate/stapel-runtime/src/commonMain/kotlin/stapel/runtime/A.kt", ambient.replace("PKG", "stapel.runtime"))
            .file("substrate/stapel-result/src/commonMain/kotlin/stapel/result/A.kt", ambient.replace("PKG", "stapel.result"))
            .file(
                "substrate/stapel-result/src/commonMain/kotlin/stapel/result/B.kt",
                "package stapel.result\n/** B. */\npublic suspend fun b(f: suspend () -> Unit): Unit { try { f() } catch (e: Exception) { println(e) } }\n",
            )
            .file("substrate/stapel-result/src/commonTest/kotlin/stapel/result/T.kt", ambient.replace("PKG", "stapel.result"))
            .module("bricks/auth/e2e", "plugins { id(\"stapel.brick-e2e\") }")
            .file(
                "bricks/auth/e2e/src/test/kotlin/stapel/auth/e2e/E.kt",
                "package stapel.auth.e2e\nclass E {\n    @org.junit.jupiter.api.Test\n    fun e() = listOf(1).add(2)\n}\n",
            )
        fx.succeeds(":stapel-runtime:checkSourceRules")
        val r = fx.fails(":stapel-result:checkSourceRules")
        assertContains(r.output, "src/commonMain/kotlin/stapel/result/A.kt:3: G-R1 Dispatchers.*")
        assertContains(r.output, "src/commonMain/kotlin/stapel/result/B.kt:3: G-R2 catch (e: Exception) swallows CancellationException")
        kotlin.test.assertFalse(r.output.contains("commonTest"), "test sources are not scanned for G-R1")
        val e2e = fx.fails(":stapel-auth-e2e:checkSourceRules")
        assertContains(e2e.output, "G-T1 test e has an expression body")
    }
}

