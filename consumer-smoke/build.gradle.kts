import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

val stapelProps = Properties().apply { rootDir.resolve("../gradle.properties").reader().use { load(it) } }
val stapelGroup: String = stapelProps.getProperty("stapel.group")
val stapelVersion: String = stapelProps.getProperty("VERSION_NAME")

kotlin {
    jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    iosArm64()
    iosSimulatorArm64()
    android {
        namespace = "smoke.consumer"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest { }
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project.dependencies.platform("$stapelGroup:stapel-bom:$stapelVersion"))
            implementation("$stapelGroup:stapel-probe")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
