package stapel.probe

/**
 * Joins [parts] with single dashes after trimming each one; blank parts are dropped.
 *
 * Exists only so the build conventions have one documented public function to compile, test,
 * dump and publish on every target.
 */
public fun probeSlug(vararg parts: String): String =
    parts.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("-") { it.lowercase() }
