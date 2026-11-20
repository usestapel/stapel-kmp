// stapel-analytics-firebase: Google Analytics for Firebase as an AnalyticsSink. Android links the vendor
// SDK; iOS takes a Swift bridge the app shell implements (ios-bridges/firebase, ruling K3); desktop is
// an honest Unsupported sink. Nothing here is a dependency of any facade, brick or stapel-app.
plugins {
    id("stapel.capability")
}

stapel {
    description = "Firebase Analytics provider for stapel-analytics: an AnalyticsSink over the vendor SDK (Android) or an app-shell Swift bridge (iOS)."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelAnalytics)
            implementation(projects.stapelDi)
        }
        androidMain.dependencies {
            implementation(libs.firebase.analytics)
        }
        commonTest.dependencies {
            implementation(projects.stapelAnalyticsTesting)
        }
    }
}
