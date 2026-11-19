// stapel-auth-ui-demo (unpublished): the auth screens on the desktop, and the UI end-to-end suite.
//
//   ./gradlew :stapel-auth-ui-demo:run
//       stories of every screen state (no backend), switchable skin / locale / mode
//   bricks/auth/e2e/stand/stand.sh up
//   ./gradlew :stapel-auth-ui-demo:run --args="--base-url http://127.0.0.1:18731/"
//       the live app (auth.login, a protected home, auth.security) against that backend; any host/port
//   ./gradlew :stapel-auth-ui-demo:jvmTest -Pstapel.e2e.baseUrl=http://127.0.0.1:18731/
//       the UI end-to-end suite (skipped without the property, so `check` stays hermetic)
plugins {
    id("stapel.brick-demo")
    id("stapel.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.stapelUiMaterial3)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(projects.stapelNetwork)
            implementation(projects.stapelSession)
            implementation(projects.stapelCache)
            implementation(projects.stapelCacheSession)
            implementation(projects.stapelConfig)
            implementation(projects.stapelRuntime)
            implementation(projects.stapelWire)
        }
    }
}

tasks.named<Test>("jvmTest") {
    providers.gradleProperty("stapel.e2e.baseUrl").orNull?.let { systemProperty("stapel.e2e.baseUrl", it) }
    testLogging { events("passed", "failed", "skipped"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

compose.desktop {
    application {
        mainClass = "stapel.auth.ui.demo.MainKt"
    }
}
