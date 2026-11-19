package stapel.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.floor
import kotlin.math.max

/**
 * Columns that fit [available] width when every cell is at least [minCellWidth] wide with [spacing]
 * between cells: `floor((available + spacing) / (minCellWidth + spacing))`, at least one. The same
 * rule as foundation's `GridCells.Adaptive`, so a lazy grid and [AdaptiveGrid] agree.
 */
public fun adaptiveColumns(available: Dp, minCellWidth: Dp, spacing: Dp): Int {
    val step = minCellWidth.value + spacing.value
    if (step <= 0f) return 1
    return max(1, floor((available.value + spacing.value) / step).toInt())
}

/**
 * A grid whose column count follows the width: as many columns as [adaptiveColumns] allows for
 * [minCellWidth], cells sharing the width equally, rows as tall as their tallest cell. Not lazy — it
 * sits inside `StapelPage`'s scroll for a screenful of cards; a long feed uses foundation's
 * `LazyVerticalGrid(GridCells.Adaptive(minCellWidth))`, which applies the same rule.
 */
@Composable
public fun <T> AdaptiveGrid(
    items: List<T>,
    minCellWidth: Dp,
    modifier: Modifier = Modifier,
    spacing: Dp = StapelTheme.spacing.s3,
    content: @Composable (T) -> Unit,
) {
    Layout(content = { items.forEach { content(it) } }, modifier = modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else (minCellWidth.roundToPx() + gap) * measurables.size
        val columns = adaptiveColumns(width.toDp(), minCellWidth, spacing).coerceAtMost(max(1, measurables.size))
        val cell = ((width - gap * (columns - 1)) / columns).coerceAtLeast(0)
        val placeables = measurables.map { it.measure(Constraints(minWidth = cell, maxWidth = cell)) }
        val rows = placeables.chunked(columns)
        val heights = rows.map { row -> row.maxOf { it.height } }
        val total = heights.sum() + gap * (rows.size - 1).coerceAtLeast(0)
        layout(width, total.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            var y = 0
            rows.forEachIndexed { r, row ->
                row.forEachIndexed { c, p -> p.place(c * (cell + gap), y) }
                y += heights[r] + gap
            }
        }
    }
}
