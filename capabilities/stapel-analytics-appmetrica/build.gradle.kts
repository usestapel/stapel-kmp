// stapel-analytics-appmetrica: AppMetrica as an AnalyticsSink. Android links the vendor
// SDK; iOS takes a Swift bridge the app shell implements (ios-bridges/appmetrica, ruling K3); desktop is
// an honest Unsupported sink. Nothing here is a dependency of any facade, brick or stapel-app.
plugins {
    id("stapel.capability")
}

stapel {
    description = "AppMetrica provider for stapel-analytics: an AnalyticsSink over the vendor SDK (Android) or an app-shell Swift bridge (iOS)."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelAnalytics)
            implementation(projects.stapelDi)
        }
        androidMain.dependencies {
            implementation(libs.appmetrica.analytics)
        }
        commonTest.dependencies {
            implementation(projects.stapelAnalyticsTesting)
        }
    }
}
