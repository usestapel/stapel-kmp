package stapel.showcase

/**
 * One cell of a story's variant matrix.
 *
 * @property label the knob values of the cell ("variant=Primary · enabled=false").
 * @property values knob key → value for the cell; other knobs keep their live value.
 */
public class MatrixCell(public val label: String, public val values: Map<String, Any?>)

/**
 * The cross product of the story's [Knob.Choice] and [Knob.Flag] knobs (or of [keys], in that order),
 * in declaration order with the last knob varying fastest. Returns null when the product exceeds
 * [maxCells] (the gallery then asks to pick fewer axes) and an empty list when no axis exists.
 */
public fun Story.matrix(keys: List<String>? = null, maxCells: Int = 48): List<MatrixCell>? {
    val axes = (keys?.map { k -> requireNotNull(knobs.firstOrNull { it.key == k }) { "story '$id' has no knob '$k'" } } ?: knobs)
        .filter { it is Knob.Choice<*> || it is Knob.Flag }
    if (axes.isEmpty()) return emptyList()
    val sizes = axes.map { axisValues(it).size }
    var total = 1L
    for (s in sizes) {
        total *= s
        if (total > maxCells) return null
    }
    var cells = listOf(emptyList<Pair<Knob<*>, Any?>>())
    for (axis in axes) {
        cells = cells.flatMap { prefix -> axisValues(axis).map { v -> prefix + (axis to v) } }
    }
    return cells.map { cell ->
        MatrixCell(
            label = cell.joinToString(" · ") { (k, v) -> "${k.key}=${describeAny(k, v)}" },
            values = cell.associate { (k, v) -> k.key to v },
        )
    }
}

/** The matrix axes of [knob]: every option of a choice, false/true of a flag. */
private fun axisValues(knob: Knob<*>): List<Any?> = when (knob) {
    is Knob.Choice<*> -> knob.options.map { it.second }
    is Knob.Flag -> listOf(false, true)
    else -> emptyList()
}

@Suppress("UNCHECKED_CAST")
internal fun describeAny(knob: Knob<*>, value: Any?): String = (knob as Knob<Any?>).describe(value)
