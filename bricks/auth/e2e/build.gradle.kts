// stapel-auth-e2e: the auth domain against a REAL backend (no mocked server). Never published.
// Stand: bricks/auth/e2e/stand/stand.sh up. Run:
//   ./gradlew :stapel-auth-e2e:test -Pstapel.e2e.baseUrl=http://127.0.0.1:18731/ \
//       -Pstapel.e2e.captchaBaseUrl=http://127.0.0.1:18732/
// Without -Pstapel.e2e.baseUrl the test task is skipped, so `check` stays hermetic.
plugins {
    id("stapel.brick-e2e")
}

dependencies {
    testImplementation(projects.stapelAuth)
    testImplementation(projects.stapelNetwork)
    testImplementation(projects.stapelSession)
    testImplementation(projects.stapelCache)
    testImplementation(projects.stapelCacheSession)
    testImplementation(projects.stapelCaptcha)
    testImplementation(projects.stapelConfig)
    testImplementation(projects.stapelRuntime)
    testImplementation(projects.stapelDi)
    testImplementation(projects.stapelMvi)
    testImplementation(projects.stapelWire)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    providers.gradleProperty("stapel.e2e.captchaBaseUrl").orNull?.let { systemProperty("stapel.e2e.captchaBaseUrl", it) }
    // Scenarios share one stand and wait on real token expiry; they run one class at a time.
    maxParallelForks = 1
    testLogging { events("passed", "failed", "skipped"); showStandardStreams = true; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
