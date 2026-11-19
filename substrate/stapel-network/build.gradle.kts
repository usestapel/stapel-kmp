plugins {
    id("stapel.substrate")
    id("stapel.serialization")
}

stapel {
    description = "Ktor-based ApiTransport for Stapel clients: one recover-once pipeline, bearer auth, no cookies."
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    // One OkHttp transport for both JVM-family targets (android and desktop): an `okhttp` group.
    applyDefaultHierarchyTemplate {
        common {
            group("okhttp") {
                withJvm()
                // The AGP multiplatform-library target is not matched by withAndroidTarget().
                withCompilations { it.target.platformType == org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.androidJvm }
            }
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelResult)
            api(projects.stapelRuntime)
            api(projects.stapelConfig)
            api(projects.stapelWire)
            implementation(projects.stapelDi)
            implementation(libs.kotlinx.io.core)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        named("okhttpMain") {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        // Per-engine wire tests: a real in-process server on a loopback socket (jvm + iOS simulator).
        jvmTest {
            kotlin.srcDir("src/engineTest/kotlin")
            dependencies {
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.cio)
            }
        }
        iosTest {
            kotlin.srcDir("src/engineTest/kotlin")
            dependencies {
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.cio)
            }
        }
    }
}
