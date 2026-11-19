# stapel-showcase — seams and invariants

- **Provides:** `Story`, `StoryKind`, `StoryState`, `StoryTarget` (`Slot`, `Screen`), `AnatomyPart`,
  `CanvasOptions`, `Knob` (`Flag`, `Choice` / `Knob.enumOf`, `Text`, `Number`, `Color`), `KnobValues`
  (Compose-observable, validated, reset per knob or whole), `Story.matrix()`, `StoryCatalog`,
  `StoryEnvironment`, `searchStories`, `catalogProblems`, `StoryScope`, `StoryCanvas`,
  `Modifier.checkerboard`.
- **Registration:** a demo module (`bricks/<brick>/demo`) or a `showcase/stories/<x>` module declares
  exactly one top-level `val storyCatalog: StoryCatalog` in its package. The gallery's build
  (`stapel.showcase-gallery`) generates its registry from those modules; nobody edits a list.
- **Coverage:** `Story.covers` names the `StapelSkin` slots and the members of the brick's screen
  table (`<Brick>Screens`) a story is evidence for. `checkDemoCoverage` (on `:showcase:gallery`) fails
  when a slot, a screen or a brick UI module has none. The declaration is the author's claim; the
  screenshot of the story is what shows it is true.
- **Invariants:** ids are lower-case (`[a-z0-9][a-z0-9._-]*`); story ids are unique per catalogue,
  catalogue ids across the gallery (`catalogProblems`); a state may only set declared knobs to legal
  values (checked at construction); `KnobValues` refuses illegal values and stores only what differs
  from the default (`modified`).
- **Value order in a story:** matrix-cell override → the state's fixed value → the live knob value.
- **Do not:** put stories, previews or playgrounds into a feature library (they ship to apps); read
  the platform locale, clock or theme inside a story — the gallery and the snapshot runner pin them.
