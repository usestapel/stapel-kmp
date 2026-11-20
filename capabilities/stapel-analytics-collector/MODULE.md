# stapel-analytics-collector — module map

- **Provides:** `CollectorSink` (`AnalyticsSink`, id `stapel`), `CollectorOptions`,
  `di.analyticsCollectorModule(options)` (contributes `analytics.stapel` on
  `NetworkQualifiers.Bare`), and the generated client of `stapel-analytics` 0.8.3 in
  `stapel.analytics.collector.api` (`AnalyticsApi`, models, typed errors).
- **Generation:** `stapelGenerate` / `stapelGenerateCheck` run the codegen CLI's `ModuleGen` entry
  (`--package stapel.analytics.collector.api --out src/commonMain/generated`) over
  `contracts/analytics`. `contracts/analytics/overlay.json` excludes one staff operation
  (`analytics_api_v1_funnels_report_retrieve`: its conversion rates trip the money heuristic,
  `E-MONEY-AS-DOUBLE`; admin screens are out of v0). Undocumented contract elements are under the
  ratchet (`contracts/analytics/ratchet.json`).
- **Wire mapping:** event → `{id, kind, name, props, userHash, ts (epoch ms), anon_id, session_id,
  source}`; batch → `{events, write_key?, anon_id, session_id, context{platform, app_version,
  os_version, locale, sdk}}`. Receipt: no rejections → accepted; `rejected[{index, reason}]` →
  `Partial` (never re-sent); 400 / 401 / 413 → the whole batch dropped as rejected with the code;
  anything else (no response, 429, 5xx) → `Retry`.
- **On zip-ties (named):** the request body is `CollectorBatch`, not the generated `Ingest`: the
  contract types `IngestEvent.ts` as `integer` without `format: int64`, which generates `Int`, too
  small for epoch milliseconds. Everything else of the operation (path, method, auth, success and
  error decoding) is the generated `AnalyticsOperations.events`. `CollectorWireTest` fails when the
  generated `Ingest` / `IngestEvent` / `IngestContext` and the body diverge. Backend follow-up:
  declare `format: int64` on `IngestEvent.ts`; then the body becomes the generated `Ingest`.
- **Invariants (tests):** one POST per batch on the bare client; ts survives as 64-bit; rejected
  entries keyed by event id; refused batches are not retried; server faults and no response are;
  the provider conformance suite passes; through the Koin graph nothing is sent before consent.
- **What not to do:** no bearer on this endpoint; no user properties (the contract has none); never
  log a property value.
