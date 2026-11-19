// stapel-config: environment, endpoints, posture and client identity. Pure data, no I/O.
plugins {
    id("stapel.substrate")
}

stapel {
    description = "Environment, service endpoints, posture and client identity for Stapel clients."
}

// The stapel-kmp version, stamped into ClientInfo.sdkVersion (User-Agent).
val sdkVersion: String = project.version.toString()
val sdkVersionDir = layout.buildDirectory.dir("generated/stapelSdkVersion/kotlin")
val generateSdkVersion = tasks.register("generateStapelSdkVersion") {
    val version = sdkVersion
    val out = sdkVersionDir
    inputs.property("version", version)
    outputs.dir(out)
    doLast {
        val file = out.get().file("stapel/config/SdkVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package stapel.config\n\n" +
                "/** The stapel-kmp version this artifact was built as. */\n" +
                "internal const val STAPEL_KMP_VERSION: String = \"$version\"\n",
        )
    }
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(generateSdkVersion.map { sdkVersionDir })
            dependencies {
                api(projects.stapelResult)
            }
        }
    }
}
