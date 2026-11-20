/** The bridge protocols' ABI versions this check was built with (read by nothing; keeps the framework non-empty). */
val stapelBridgeAbis: Map<String, Int> = mapOf(
    "firebase-analytics" to stapel.analytics.firebase.FIREBASE_ANALYTICS_BRIDGE_ABI,
    "appmetrica-analytics" to stapel.analytics.appmetrica.APPMETRICA_ANALYTICS_BRIDGE_ABI,
    "firebase-push" to stapel.push.firebase.FIREBASE_PUSH_BRIDGE_ABI,
)
