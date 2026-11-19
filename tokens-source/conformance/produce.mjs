#!/usr/bin/env node
// Regenerates the conformance corpus under tokens-source/conformance/expected/
// from the Node implementation of @stapel/tokens — the canonical resolver the
// Kotlin port in tools/tokens-gen is held equal to.
//
//   node tokens-source/conformance/produce.mjs --base <dir> --patched <dir>
//
//   --base     packages/tokens of stapel-react at SOURCE.json `ref`, unmodified
//              (the published `stapel-tokens` bin): gives every case's
//              validation errors/warnings and, for a valid theme, tokens.css.
//   --patched  the same tree with the `resolved@1` target applied
//              (SOURCE.json `corpus.resolvedPatchSha256`): gives
//              tokens.resolved.json, or the resolved@1 refusal.
//
// Each case is a host theme deep-merged over the bundled theme.default.json,
// exactly as a host runs the bin. Output is deterministic; re-running it on the
// same inputs reproduces the committed files byte for byte.
import { spawnSync } from "node:child_process";
import { mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync, mkdirSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const args = Object.fromEntries(
  process.argv.slice(2).reduce((acc, a, i, all) => (a.startsWith("--") ? [...acc, [a.slice(2), all[i + 1]]] : acc), [])
);
if (!args.base || !args.patched) {
  console.error("usage: produce.mjs --base <packages/tokens> --patched <packages/tokens with resolved@1>");
  process.exit(2);
}
const casesDir = join(here, "cases");
const expectedDir = join(here, "expected");
mkdirSync(expectedDir, { recursive: true });

function run(pkgDir, theme, target) {
  const out = mkdtempSync(join(tmpdir(), "stapel-tokens-corpus-"));
  const r = spawnSync(
    process.execPath,
    [join(resolve(pkgDir), "bin", "stapel-tokens.mjs"), "--theme", theme, "--targets", target, "--out", out],
    { encoding: "utf8" }
  );
  const files = {};
  for (const f of existsSync(out) ? readdirSync(out) : []) files[f] = readFileSync(join(out, f), "utf8");
  rmSync(out, { recursive: true, force: true });
  return { status: r.status, stderr: r.stderr.split("\n").filter(Boolean), files };
}

const strip = (prefix, lines) => lines.filter((l) => l.startsWith(prefix)).map((l) => l.slice(prefix.length));

for (const file of readdirSync(casesDir).filter((f) => f.endsWith(".theme.json")).sort()) {
  const name = file.slice(0, -".theme.json".length);
  const theme = join(casesDir, file);
  const core = run(args.base, theme, "core");
  const resolved = run(args.patched, theme, "resolved@1");
  const valid = core.status === 0;
  const expect = {
    case: name,
    valid,
    errors: strip("✖ ", core.stderr),
    warnings: strip("⚠ ", core.stderr),
    resolvedError: valid && resolved.status !== 0 ? resolved.stderr[resolved.stderr.length - 1] : null,
  };
  writeFileSync(join(expectedDir, `${name}.expect.json`), `${JSON.stringify(expect, null, 2)}\n`);
  for (const suffix of ["tokens.css", "tokens.resolved.json"]) {
    const path = join(expectedDir, `${name}.${suffix}`);
    const content = suffix === "tokens.css" ? core.files["tokens.css"] : resolved.files["tokens.resolved.json"];
    if (content !== undefined) writeFileSync(path, content);
    else rmSync(path, { force: true });
  }
  console.error(`${name}: ${valid ? "valid" : `${expect.errors.length} error(s)`}${expect.resolvedError ? " (resolved@1 refused)" : ""}`);
}
