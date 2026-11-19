package shop

import stapel.runtime.PlatformContext

/** The platform handle in tests (jvm and the iOS simulator; this app has no Android host tests). */
expect fun testPlatformContext(): PlatformContext

/** `desktop` on the JVM, `ios` on the simulator. */
expect val testTarget: String
