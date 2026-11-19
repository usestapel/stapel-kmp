package stapel.runtime

import kotlin.test.Test

/**
 * The platform logger writes through NSLog without crashing, including text that contains format
 * directives. Before the fix the first record crashed the process (SIGSEGV): a Kotlin String passed as
 * an NSLog vararg is not bridged to an NSString.
 */
class IosLoggerTest {
    @Test
    fun recordsReachNsLogWithoutCrashing() {
        val logger = platformLogger(LogLevel.Verbose)
        logger.info("stapel.test") { "stapel.app.ready" }
        logger.warn("stapel.test") { "100% done, %@ %s %d stay literal" }
        logger.log(LogLevel.Error, "stapel.test", "with fields", IllegalStateException("boom"), mapOf("k" to "v%"))
    }
}
