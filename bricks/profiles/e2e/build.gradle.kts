// stapel-profiles-e2e: the profiles brick against a REAL backend (auth + profiles + cdn scaffolded locally,
// prototype posture, mock OTP), wired the way an app wires it: stapelApp { bricks(AuthBrick(), ProfilesBrick()) }.
// Never published. Stand: bricks/profiles/e2e/stand/stand.sh up. Run:
//   ./gradlew :stapel-profiles-e2e:test -Pstapel.e2e.baseUrl=http://127.0.0.1:18761/
// Without -Pstapel.e2e.baseUrl the test task is skipped, so `check` stays hermetic.
plugins {
    id("stapel.brick-e2e")
}

dependencies {
    testImplementation(projects.stapelProfiles)
    testImplementation(projects.stapelProfilesUi)
    testImplementation(projects.stapelAuth)
    testImplementation(projects.stapelAuthUi)
    testImplementation(projects.stapelApp)
    testImplementation(projects.stapelNavigation)
    testImplementation(projects.stapelSession)
    testImplementation(projects.stapelConfig)
    testImplementation(projects.stapelRuntime)
    testImplementation(projects.stapelDi)
    testImplementation(projects.stapelMvi)
    testImplementation(projects.stapelWire)
    testImplementation(projects.stapelResult)
    testImplementation(libs.kotlinx.coroutines.core)
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 1
    testLogging { events("passed", "failed", "skipped"); showStandardStreams = true; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
