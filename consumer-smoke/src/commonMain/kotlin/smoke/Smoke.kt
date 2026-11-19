package smoke

import stapel.probe.probeSlug

/** Uses the published artifact from common code (all four targets). */
fun smokeSlug(): String = probeSlug("Consumer", "Smoke")
