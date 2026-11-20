// stapel-push: the push facade. Vendor SDKs live only in provider artifacts (stapel-push-firebase);
// the backend seam is PushDeviceRegistry (stapel-notifications-push). No vendor coordinate here.
plugins {
    id("stapel.substrate")
}

stapel {
    description = "Push notifications for Stapel clients: a provider-neutral facade, permission flow, token lifecycle bound to the session, tap routing into deep links."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelResult)
            api(projects.stapelRuntime)
            api(projects.stapelSession)
            implementation(projects.stapelDi)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(projects.stapelTesting)
            implementation(projects.stapelPushTesting)
        }
    }
}
