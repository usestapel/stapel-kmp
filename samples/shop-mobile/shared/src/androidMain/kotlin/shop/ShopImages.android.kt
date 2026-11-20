package shop

import stapel.runtime.PlatformContext
import stapel.runtime.application

internal actual fun coilContext(platform: PlatformContext): coil3.PlatformContext = platform.application
