# stapel-showcase

The story API of Stapel clients (Compose Multiplatform, Material-free): a **story** is one component,
composite or screen with named **states** (the snapshot units), typed **knobs** (boolean, enum/choice,
text, int range, colour role) with reset, **anatomy** notes and **canvas** options (checkerboard,
padding, max width). A module exports one **`StoryCatalog`**; the showcase gallery, the screenshot
baselines and the docs export all read the same catalogues.

Coordinates: `io.github.usestapel:stapel-showcase`. Published so a project can write stories for its
own screens; stories never live inside a feature library — put them in a demo module.

```kotlin
private val variant = Knob.enumOf("variant", "Variant", ButtonVariant.Primary)
private val loading = Knob.Flag("loading", "Loading")

val storyCatalog = StoryCatalog(
    id = "shop", title = "Shop",
    stories = listOf(
        Story(
            id = "checkout.pay", title = "Pay button", group = "Checkout", knobs = listOf(variant, loading),
            states = listOf(StoryState("busy", "Paying", mapOf("loading" to true))),
            covers = setOf(StoryTarget.Slot("Button")),
        ) { StapelButton("Pay", { event("click") }, variant = value(variant), loading = value(loading)) },
    ),
)
```

`StoryCanvas(story, state, values)` draws a story on its canvas inside whatever theme the caller set
up; `Story.matrix()` gives the cross product of its choice and flag knobs; `searchStories` searches a
set of catalogues.
