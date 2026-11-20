# stapel-analytics-testing

Test kit for `stapel-analytics`: `FakeAnalyticsSink` (scriptable sink that records batches and
vendor calls), `RecordingAnalytics` (an `Analytics` for presenter and flow tests), and
`AnalyticsSinkConformance`, the suite every analytics provider artifact subclasses.

Coordinates: `io.github.usestapel:stapel-analytics-testing` (group provisional until the first publish).

```kotlin
class MySinkConformanceTest : AnalyticsSinkConformance() {
    override fun configured() = RecordingBridge().let { SinkUnderTest(mySink(it), it) }
    override fun unconfigured() = RecordingBridge(configured = false).let { SinkUnderTest(mySink(it), it) }
    @Test fun conformance() = runTest { assertEquals(caseNames.size, verify()) }
}
```
