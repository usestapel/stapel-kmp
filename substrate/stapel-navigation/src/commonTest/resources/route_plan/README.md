# Shared route-plan fixtures

Produced by stapel-tools' planner (`stapel_tools._frontend_templates.build_nav_route_plan`), consumed
verbatim by `RoutePlanFixtureTest`, which replays every case through the Kotlin `resolveNav`. The
Python planner is the reference: if the two disagree, the Kotlin port is wrong or the planner changed.

- Source: `usestapel/stapel-tools` at the commit recorded in `index.json` and in every case's
  `source.commit` (2026-11-19: `741df8d74c520c4213676b997611d2df144e500a`, `origin/main`, v0.81.1).
- How: a read-only clone at `origin/main`, installed into a throwaway Python 3.12 venv, then
  `python -I generate.py --tools-commit <sha> --out .` (usage in the script's docstring).
- Cases: every registry pair alone, all pairs together, auth + profiles + notifications (the
  orphan rule both ways), and synthetic inputs for hoisting, absolute-parent children, grandchildren,
  order ties and the three generation-time refusals. Synthetic ids are added to the planner's
  declared-id set for the run (recorded per case in `declared`).
- `expected.unrouted` is computed from the planner's output (input ids it did not route), not by
  re-implementing the rule.
- Never edit a JSON file by hand; re-run the script and commit the result.
- Mobile-only manifest fields (`MOBILE_ONLY_ENTRY_FIELDS`: `presentation`, `pane`, `flow`,
  `pageSlots`, `requires`, `aliases`) are not in the planner's input and are not compared. For
  `aliases` the replay additionally resolves every case with a synthetic alias on each entry and
  requires the same routed paths: an alias is an extra way in, never a different canonical path.
