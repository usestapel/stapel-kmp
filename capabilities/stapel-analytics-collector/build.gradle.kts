// stapel-analytics-collector: the first-party AnalyticsSink — POST /analytics/api/v1/events of the
// project's own stapel-analytics brick, through the generated client of contracts/analytics.
plugins {
    id("stapel.capability")
    id("stapel.serialization")
}

stapel {
    description = "First-party analytics sink for Stapel clients: delivers hub batches to the stapel-analytics brick's ingest endpoint."
    ratchetedPackages.add("stapel\\.analytics\\.collector\\.api(\\..*)?")
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir("src/commonMain/generated/kotlin")
            dependencies {
                api(projects.stapelAnalytics)
                api(projects.stapelWire)
                implementation(projects.stapelNetwork)
                implementation(projects.stapelDi)
            }
        }
        commonTest.dependencies {
            implementation(projects.stapelTesting)
            implementation(projects.stapelAnalyticsTesting)
        }
    }
}

// The brick's API is generated from the pinned contract into this module's own package
// (stapel.analytics.collector.api) by the codegen CLI's ModuleGen entry; drift-gated like a brick domain.
val codegen = configurations.create("stapelCodegen") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies { add(codegen.name, project(":tools:codegen:cli")) }

fun JavaExec.moduleGen(check: Boolean) {
    group = "stapel generate"
    classpath = codegen
    mainClass.set("stapel.tools.codegen.cli.ModuleGen")
    args(
        listOfNotNull(
            if (check) "--check" else null,
            "--brick", "analytics", "--module", project.name, "--package", "stapel.analytics.collector.api",
            "--out", layout.projectDirectory.dir("src/commonMain/generated").asFile.absolutePath,
            "--repo-root", rootDir.absolutePath,
        ),
    )
    inputs.dir(rootProject.layout.projectDirectory.dir("contracts/analytics")).withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.register<JavaExec>("stapelGenerate") {
    description = "Regenerates src/commonMain/generated from contracts/analytics."
    moduleGen(check = false)
    outputs.dir("src/commonMain/generated")
}

val generateCheck = tasks.register<JavaExec>("stapelGenerateCheck") {
    description = "Regenerates in memory and fails on any difference from the committed generated sources."
    moduleGen(check = true)
    inputs.dir("src/commonMain/generated").optional()
    outputs.upToDateWhen { false }
}
tasks.named("check") { dependsOn(generateCheck) }
