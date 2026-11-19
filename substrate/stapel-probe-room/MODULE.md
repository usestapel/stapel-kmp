# stapel-probe-room — module map

- **Provides:** `ProbeDatabase`, `ProbeNoteDao`, `ProbeNote`. No seams, no consumers.
- **Schemas:** `schemas/` is committed (Room 3 requires a schema directory).
- **Lifetime:** deleted by the build owner when `stapel-cache-room` is green.
