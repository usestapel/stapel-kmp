package shop

import stapel.runtime.PlatformContext

internal actual fun coilContext(platform: PlatformContext): coil3.PlatformContext = coil3.PlatformContext.INSTANCE
