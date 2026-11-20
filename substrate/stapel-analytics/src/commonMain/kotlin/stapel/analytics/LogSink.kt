package stapel.analytics

import stapel.runtime.StapelLogger
import stapel.runtime.info

/**
 * A sink that writes one line per delivered event through the app's logger:
 * `stapel.analytics event <kind> <name>` (tag [ANALYTICS_LOG_TAG]). Property values are never logged,
 * only their keys. For development builds and launch smokes; works on every platform.
 *
 * @param logger the app's logger.
 * @param id the sink id; `log` by default.
 */
public class LogSink(private val logger: StapelLogger, override val id: String = "log") : AnalyticsSink {
    override val availability: SinkAvailability get() = SinkAvailability.Ready
    override val purposes: Set<ConsentPurpose> get() = setOf(ConsentPurpose.Analytics)

    override fun setCollectionEnabled(enabled: Boolean) {
        logger.info(ANALYTICS_LOG_TAG) { "stapel.analytics collection $id $enabled" }
    }

    override suspend fun deliver(batch: List<AnalyticsEvent>): SinkReceipt {
        batch.forEach { e -> logger.info(ANALYTICS_LOG_TAG) { "stapel.analytics event ${e.kind.wire} ${e.name} ${e.props.keys.sorted()}" } }
        return SinkReceipt.Accepted
    }

    override fun setUserProperty(name: String, value: String?) {
        logger.info(ANALYTICS_LOG_TAG) { "stapel.analytics user-property $name ${if (value == null) "cleared" else "set"}" }
    }

    override fun reset() {
        logger.info(ANALYTICS_LOG_TAG) { "stapel.analytics reset $id" }
    }
}
