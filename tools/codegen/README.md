# stapel-codegen

Generates a brick's Kotlin API layer (models, `<Brick>Api` + client, operation specs, typed errors,
pagers, cache keys and invalidation) from its pinned backend contract. JVM only, KotlinPoet, never
published in v0. Design: ARCH-CODEGEN; runtime it targets: `substrate/stapel-wire`.

| Module | What |
|---|---|
| `tools/codegen/core` | contract parser → IR → emitters, pins, report, ratchet |
| `tools/codegen/cli` | `stapel.tools.codegen.cli.MainKt` (what `stapelGenerate` / `stapelGenerateCheck` run) |

## Inputs per brick: `contracts/<brick>/`

| File | Owner | What |
|---|---|---|
| `pin.json` | `pins sync` | repo, tag, commit (`rev-parse '<tag>^{commit}'`), version, and per vendored file its source path and sha256 |
| `schema.json`, `errors.json`, `flows.json`, `capabilities.json` | vendored bytes | the brick's committed contract at the pinned commit; `gen` refuses to run when a digest differs |
| `overlay.json` | reviewed by hand | interim corrections of the contract, each naming the source patch it stands in for (`fixes`) and its evidence (`source`): `errors` (status → codes), `auth` (`optional`/`none`), `verification`, `captcha`, `alias`, `exclude` |
| `cache.json` | reviewed by hand | cache plan (ruling R10): per operation `key`, `policy`, `persistence`, `scope`, `sensitive`, `invalidates`, `offline` — see `CachePlan.kt` |
| `ratchet.json` | `gen --update-ratchet` | allowed count per diagnostic code and the overlay entry budget; counts may only go down |

A pin bump is one commit: `pins sync`, vendored files, overlay entries the new contract makes
redundant removed (`E-OVERLAY-REDUNDANT` names them), regenerated sources, `gen --update-ratchet`.

## Commands

```sh
./gradlew :tools:codegen:cli:installDist            # build/install/stapel-codegen/bin/stapel-codegen
stapel-codegen pins sync --brick auth --module stapel-auth --repo <clone> --repo-url <url> --tag v0.52.0 \
    --file schema.json=docs/schema.v2.json           # vendor + pin.json
stapel-codegen pins check --brick auth [--repo <clone>]
./gradlew :stapel-auth:stapelGenerate               # gen
./gradlew :stapel-auth:stapelGenerateCheck          # gen --check: regenerate in memory, byte-compare, exit 1 on drift
stapel-codegen inspect contracts/auth               # census + diagnostics, nothing written
stapel-codegen smoke --src <dir of brick clones>    # generate every contract found, report a table
```

## Gates

Hard errors (`E-*`) stop generation: `E-UNION-AMBIGUOUS`, `E-MONEY-AS-DOUBLE`, `E-NAME-COLLISION`,
`E-ERR-UNKNOWN-CODE`, `E-UNSUPPORTED-KEYWORD`, `E-OVERLAY-STALE`, `E-OVERLAY-REDUNDANT`, `E-CACHE-PLAN`,
`E-PIN-DIGEST`, `E-NAME-LOCK`. Warnings (`W-*`, including the undocumented-element codes
`W-DOC-OPERATION|PARAM|TYPE|PROPERTY`) are counted in `codegen-report.json` and held by the ratchet:
no count above its baseline, a lower count must be committed as the new baseline, and an undocumented
element absent from the previous report fails the run (fix it in the brick's schema). Each gate has
an inversion fixture in `core/src/test`.
