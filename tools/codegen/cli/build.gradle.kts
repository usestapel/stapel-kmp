// stapel-codegen CLI: `gen [--check]`, `inspect`, `pins sync|check`. Entry point
// stapel.tools.codegen.cli.MainKt is what the brick-domain `stapelGenerate` hooks run.
plugins {
    id("stapel.jvm-tool")
    application
}

application {
    mainClass.set("stapel.tools.codegen.cli.MainKt")
    applicationName = "stapel-codegen"
}

dependencies {
    implementation(projects.tools.codegen.core)
}

tasks.withType<Test>().configureEach {
    systemProperty("stapel.repoRoot", rootDir.absolutePath)
}
