plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

compose.desktop {
    application {
        mainClass = "shop.desktop.MainKt"
        nativeDistributions {
            packageName = "shop-mobile"
            packageVersion = "1.0.0"
        }
    }
}

// Launch smoke: `./gradlew :desktopApp:run --args=--smoke` opens the window, waits for the app's
// `stapel.app.ready` marker and exits 0 (1 when it does not appear within 30 s).
