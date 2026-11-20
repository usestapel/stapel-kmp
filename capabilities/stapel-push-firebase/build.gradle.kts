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
        // The Android bridge on the JVM (Robolectric): intents, channels, foreground posting, the prompt activity.
        getByName("androidHostTest").dependencies {
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
            implementation(projects.stapelSession)
        }
    }
}

// Dokka analyses the Android source set with the inherited members of StapelFirebaseMessagingService
// (FirebaseMessagingService), whose types (play-services cloud messaging) firebase-messaging keeps off
// the compile classpath: unresolved-link warnings fail the docs gate. The Android source set is not
// rendered; its public API (androidFirebasePushProvider, firebasePushModule, handleFirebasePushIntent)
// is KDoc'd in source and listed in MODULE.md.
extensions.configure<org.jetbrains.dokka.gradle.DokkaExtension> {
    dokkaSourceSets.matching { it.name == "androidMain" }.configureEach { suppress.set(true) }
}
