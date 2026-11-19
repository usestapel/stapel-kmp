# showcase/gallery

Unpublished. The Stapel showcase: every story catalogue of the repository — `showcase/stories/*`
(stapel-ui: foundations, one story per `StapelSkin` slot, composites) and every `bricks/<brick>/demo`
— discovered at build time by the `stapel.showcase-gallery` plugin (generated registry, no list to
edit). Run it:

    ./gradlew :showcase:desktop:run
    ./gradlew :showcase:desktop:run --args="--open /s/ui/button?skin=Foundation&mode=Both"

- **Navigation** on `stapel-navigation`: start page, a page per catalogue (its families), a page per
  story; every page has a route and a link (`stapel-showcase://gallery/s/<catalogue>/<story>?…`).
  Search over titles, ids, groups and descriptions.
- **Global controls**, applied to every canvas and mirrored in links: skin (Material 3 / Foundation),
  theme preset, light / dark / both side by side, locale (every locale a catalogue ships), size class
  (phone 390 / tablet 768 / desktop 1280 frame), text scale (100–200 %), reduce motion, RTL.
- **Story page:** Playground (canvas, knobs with per-knob and global reset, event log), States (every
  named state side by side), Matrix (cross product of choice and flag knobs), Anatomy (numbered part
  outlines), Usage (id, coverage, states, knobs, snippet); "copy story id" and "copy link".
- **Screenshots and docs:** `recordScreenshots` / `verifyScreenshots` (macOS) and `exportCatalog`,
  see docs/gates.md; `checkDemoCoverage` is the coverage gate.
