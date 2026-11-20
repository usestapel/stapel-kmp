// stapel-push-firebase: the Firebase Cloud Messaging provider of stapel-push. Android uses FCM directly
// (firebase-messaging, a messaging service and a permission activity in this artifact's manifest); iOS
// declares FirebasePushBridge, implemented in Swift by the app (ios-bridges/firebase/, emitted by the
// scaffold) — no cinterop, no SwiftPM import, no CocoaPods here (ruling K3); desktop reports unsupported.
plugins {
    id("stapel.capability")
}

stapel {
    description = "Firebase Cloud Messaging provider for stapel-push: FCM on Android, an Objective-C-exportable bridge for the app's Swift half on iOS, unsupported on desktop."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelPush)
            implementation(projects.stapelDi)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.firebase.messaging)
        }
        commonTest.dependencies {
            implementation(projects.stapelPushTesting)
            implementation(projects.stapelTesting)
        }
    }
}
