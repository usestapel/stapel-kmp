// stapel-image-compose: the Coil 3 loader behind stapel-ui's image seam (LocalStapelImages). The app builds
// one ImageLoader (stapelImageLoader) on its own Ktor HttpClient — no second HTTP stack — and provides it
// once; brick UI never sees a Coil type. Authenticated image hosts get the session's bearer token; every
// other host gets none (proven on the wire against a loopback server, jvm + iOS simulator).
plugins {
    id("stapel.substrate-ui")
}

stapel {
    description = "Coil 3 image loading for Stapel UI: the loader behind LocalStapelImages, bearer only for the app's own hosts."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelUi)
            api(projects.stapelImage)
            api(projects.stapelNetwork)
            api(projects.stapelSession)
            api(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            api(libs.ktor.client.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        getByName("skikoTest").dependencies {
            implementation(libs.coil.test)
        }
        // Wire tests: a real in-process server on a loopback socket, reached by the platform engine.
        jvmTest {
            kotlin.srcDir("src/wireTest/kotlin")
            dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.cio)
            }
        }
        iosTest {
            kotlin.srcDir("src/wireTest/kotlin")
            dependencies {
                implementation(libs.ktor.client.darwin)
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.cio)
            }
        }
    }
}
