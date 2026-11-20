import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

// Same name, static linkage and iOS floor as the scaffolded app's framework, so the bridges see the
// protocols under the names an app sees them.
val IOS_FLOOR = "15.0"
val stapelGroup = "io.github.usestapel"
val stapelVersion: String = providers.gradleProperty("stapel.version").get()
val bridged = listOf("stapel-analytics-firebase", "stapel-analytics-appmetrica", "stapel-push-firebase")

kotlin {
    val xcf = XCFramework("Shared")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            bridged.forEach { export("$stapelGroup:$it:$stapelVersion") }
            xcf.add(this)
        }
        target.binaries.configureEach {
            freeCompilerArgs += "-Xoverride-konan-properties=osVersionMin.ios_arm64=$IOS_FLOOR;osVersionMin.ios_simulator_arm64=$IOS_FLOOR"
        }
    }
    sourceSets {
        iosMain.dependencies {
            bridged.forEach { api("$stapelGroup:$it:$stapelVersion") }
        }
    }
}
