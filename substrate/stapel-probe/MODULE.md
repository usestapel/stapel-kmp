# stapel-probe — module map

- **Provides:** `probeSlug` only. No seams, no consumers.
- **Why it exists:** the bootstrap slice proves every convention plugin on a real module
  (`docs/gates.md`). Nothing may depend on it except the consumer smoke build and the BOM.
- **Lifetime:** deleted by the build owner when the first real substrate modules are green.
