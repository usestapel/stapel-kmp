# stapel-probe-compose — module map

- **Provides:** `ProbeLabel`, `PROBE_LABEL_TAG`. No seams, no consumers.
- **Shows the UI test layout:** `src/skikoTest` (jvm + iOS) holds `runComposeUiTest` tests;
  Android host tests do not run Compose UI tests.
- **Lifetime:** deleted by the build owner when the first real UI modules are green.
