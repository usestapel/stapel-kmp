// stapel-notifications-e2e: push registration against a REAL backend (auth + notifications scaffolded locally,
// prototype posture, mock OTP, the mock push sender), wired the way an app wires it:
// stapelApp { bricks(AuthBrick()); modules(pushModule(...), notificationsPushModule(), <provider>) } with
// FakePushProvider standing in for the vendor SDK. Never published. Stand: bricks/notifications/e2e/stand/stand.sh up.
//   ./gradlew :stapel-notifications-e2e:test -Pstapel.e2e.baseUrl=http://127.0.0.1:18771/
// Without -Pstapel.e2e.baseUrl the test task is skipped, so `check` stays hermetic.
plugins {
    id("stapel.brick-e2e")
}

dependencies {
    testImplementation(projects.stapelNotificationsPush)
    testImplementation(projects.stapelNotifications)
    testImplementation(projects.stapelPush)
    testImplementation(projects.stapelPushTesting)
    testImplementation(projects.stapelAuth)
    testImplementation(projects.stapelAuthUi)
    testImplementation(projects.stapelApp)
    testImplementation(projects.stapelSession)
    testImplementation(projects.stapelConfig)
    testImplementation(projects.stapelRuntime)
    testImplementation(projects.stapelDi)
    testImplementation(projects.stapelMvi)
    testImplementation(projects.stapelResult)
    testImplementation(libs.kotlinx.coroutines.core)
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 1
    testLogging { events("passed", "failed", "skipped"); showStandardStreams = true; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
