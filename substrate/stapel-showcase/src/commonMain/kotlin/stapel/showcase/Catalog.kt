package stapel.showcase

import androidx.compose.runtime.Composable
import stapel.i18n.I18nBundle

/**
 * What a catalogue's stories need around them: string bundles, the locales they ship, and any
 * composition locals (an error-text resolver, a skin extension). The gallery and the snapshot runner
 * compose the environment of the story's catalogue around every story.
 *
 * @property bundles string bundles registered after the core UI bundle.
 * @property locales the locales the stories are meant to be seen in; the gallery offers their union.
 * @property provide wraps every story (inside the theme) with the catalogue's own composition locals.
 */
public class StoryEnvironment(
    public val bundles: List<I18nBundle> = emptyList(),
    public val locales: List<String> = listOf("en"),
    public val provide: @Composable (content: @Composable () -> Unit) -> Unit = { it() },
) {
    /** Defaults. */
    public companion object {
        /** No bundles beyond the core UI floor, English only, nothing provided. */
        public val None: StoryEnvironment = StoryEnvironment()
    }
}

/**
 * The stories one module exports. Each demo module (`bricks/<brick>/demo`) and each
 * `showcase/stories/<x>` module declares exactly one, as the top-level `val storyCatalog` in its
 * package; the gallery's registry is generated from those modules at build time.
 *
 * @property id stable, repo-wide unique (`ui`, `auth`); the first segment of a story's qualified id.
 * @property title the catalogue's name in the gallery.
 * @property stories in catalogue order.
 * @property environment what the stories need around them.
 */
public class StoryCatalog(
    public val id: String,
    public val title: String,
    public val stories: List<Story>,
    public val environment: StoryEnvironment = StoryEnvironment.None,
) {
    init {
        require(ID.matches(id) && '.' !in id) { "catalogue id '$id' must match [a-z0-9][a-z0-9_-]*" }
        require(stories.isNotEmpty()) { "catalogue '$id' has no stories" }
        duplicates(stories.map { it.id })?.let { throw IllegalArgumentException("catalogue '$id' declares story '$it' twice") }
    }

    /** The story named [storyId], or null. */
    public fun story(storyId: String): Story? = stories.firstOrNull { it.id == storyId }

    /** The catalogue's group paths in first-appearance order (the family pages). */
    public val groups: List<String> get() = stories.map { it.group }.distinct()

    /** The `<catalogue>/<story>` id of [story]: what "copy story id" copies and links name. */
    public fun qualifiedId(story: Story): String = "$id/${story.id}"
}

/**
 * One search result.
 *
 * @property catalog the story's catalogue.
 * @property story the story.
 * @property rank lower is better: 0 exact id, 1 title prefix, 2 id/title substring, 3 group or description.
 */
public class StoryHit(public val catalog: StoryCatalog, public val story: Story, public val rank: Int)

/**
 * Searches titles, ids (plain and qualified), groups and descriptions of every story, case-insensitive;
 * all words of [query] must match. Results are ordered by rank, then catalogue order. A blank query
 * returns nothing.
 */
public fun searchStories(catalogs: List<StoryCatalog>, query: String): List<StoryHit> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    val hits = mutableListOf<StoryHit>()
    for (catalog in catalogs) {
        for (story in catalog.stories) {
            val id = story.id.lowercase()
            val qualified = catalog.qualifiedId(story).lowercase()
            val title = story.title.lowercase()
            val rest = (story.group + " " + (story.description ?: "") + " " + catalog.title).lowercase()
            val ranks = words.map { w ->
                when {
                    w == id || w == qualified -> 0
                    title.startsWith(w) -> 1
                    w in id || w in qualified || w in title -> 2
                    w in rest -> 3
                    else -> null
                }
            }
            if (ranks.all { it != null }) hits += StoryHit(catalog, story, ranks.maxOf { it!! })
        }
    }
    return hits.sortedBy { it.rank }
}

/**
 * Checks that catalogue ids are unique across [catalogs] (story ids are unique within a catalogue by
 * construction, so qualified ids are then unique repo-wide); returns the problems found.
 */
public fun catalogProblems(catalogs: List<StoryCatalog>): List<String> =
    catalogs.groupBy { it.id }.filterValues { it.size > 1 }.keys.map { "catalogue id '$it' is declared by ${catalogs.count { c -> c.id == it }} modules" }
