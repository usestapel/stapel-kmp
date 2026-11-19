package stapel.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import stapel.i18n.CoreUiStrings
import stapel.ui.skin.ListItemAccessory
import stapel.ui.skin.ListItemSpec
import stapel.ui.skin.TextFieldKind
import stapel.ui.skin.TextFieldSpec
import stapel.ui.skin.ToggleKind

/** Test tag suffix of a [PickerSheet] option row: `<testTag>.option.<index in options>`. */
public const val PICKER_OPTION_TAG: String = ".option."

/** Test tag suffix of a [PickerSheet]'s search field. */
public const val PICKER_SEARCH_TAG: String = ".search"

/** Tallest a picker's option list grows before it scrolls. */
public val PICKER_LIST_MAX_HEIGHT: Dp = 420.dp

/**
 * Case- and accent-insensitive "contains" used by the picker search (`é` matches `e`, `Ё` matches `е`).
 */
public fun pickerMatches(label: String, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return fold(label).contains(fold(q))
}

private fun fold(text: String): String = buildString(text.length) {
    for (c in text.lowercase()) {
        append(
            when (c) {
                'à', 'á', 'â', 'ã', 'ä', 'å', 'ą', 'ā' -> 'a'
                'ç', 'č', 'ć' -> 'c'
                'ď' -> 'd'
                'è', 'é', 'ê', 'ë', 'ě', 'ę', 'ē' -> 'e'
                'ì', 'í', 'î', 'ï', 'ī' -> 'i'
                'ł', 'ľ', 'ĺ' -> 'l'
                'ñ', 'ň', 'ń' -> 'n'
                'ò', 'ó', 'ô', 'õ', 'ö', 'ő', 'ø' -> 'o'
                'ř' -> 'r'
                'š', 'ś', 'ș', 'ş' -> 's'
                'ť', 'ț' -> 't'
                'ù', 'ú', 'û', 'ü', 'ů', 'ű', 'ū' -> 'u'
                'ý', 'ÿ' -> 'y'
                'ž', 'ź', 'ż' -> 'z'
                'ё' -> 'е'
                'й' -> 'и'
                else -> c
            },
        )
    }
}

/**
 * Picks one or several of [options] in a sheet (a modal on wide windows): never a dropdown. The rows
 * are the skin's list items with a radio (single) or checkbox (multiple) accessory, in a lazy list —
 * ten thousand options compose only the visible rows. With [searchLabel] a search field filters the
 * rows by [optionLabel] (case- and accent-insensitive); an empty result shows [noMatchesText].
 *
 * Single choice: a tap calls [onSelect] with that option and dismisses. Multiple: taps toggle a draft;
 * [doneLabel] commits it through [onSelect] and dismisses; dismissing otherwise drops the draft.
 *
 * @param dismissLabel accessible name of the dismiss affordance (i18n copy).
 * @param key stable identity of an option for the lazy list (the option itself by default).
 */
@Composable
public fun <T> PickerSheet(
    title: String,
    options: List<T>,
    optionLabel: (T) -> String,
    selected: Set<T>,
    onSelect: (Set<T>) -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = stapelString(CoreUiStrings.Close),
    multiple: Boolean = false,
    doneLabel: String? = null,
    searchLabel: String? = null,
    clearLabel: String? = stapelString(CoreUiStrings.Clear),
    noMatchesText: String? = null,
    optionSupporting: ((T) -> String?)? = null,
    key: ((T) -> Any)? = null,
    testTag: String? = null,
) {
    var draft by remember(selected) { mutableStateOf(selected) }
    val query = remember { TextFieldState() }
    val filtered by remember(options, optionLabel) {
        derivedStateOf {
            val q = query.text.toString()
            if (q.isBlank()) options.withIndex().toList() else options.withIndex().filter { pickerMatches(optionLabel(it.value), q) }
        }
    }
    val done = doneLabel ?: stapelString(CoreUiStrings.Done)
    StapelDialog(
        onDismiss = onDismiss,
        dismissLabel = dismissLabel,
        title = title,
        testTag = testTag,
        footer = if (multiple) {
            { StapelButton(done, { onSelect(draft); onDismiss() }, testTag = testTag?.plus(".done")) }
        } else {
            null
        },
    ) {
        Column {
            if (searchLabel != null) {
                StapelTheme.skin.TextField(
                    TextFieldSpec(query, searchLabel, TextFieldKind.Search, clearLabel = clearLabel, testTag = testTag?.plus(PICKER_SEARCH_TAG)),
                    Modifier.fillMaxWidth(),
                )
            }
            if (filtered.isEmpty()) {
                EmptyState(noMatchesText ?: stapelString(CoreUiStrings.NoMatches))
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = PICKER_LIST_MAX_HEIGHT)) {
                    itemsIndexed(filtered, key = { _, item -> key?.invoke(item.value) ?: item.index }) { _, item ->
                        val option = item.value
                        val checked = option in draft
                        StapelTheme.skin.ListItem(
                            ListItemSpec(
                                headline = optionLabel(option),
                                supporting = optionSupporting?.invoke(option),
                                accessory = ListItemAccessory.Toggle(
                                    if (multiple) ToggleKind.Checkbox else ToggleKind.Radio,
                                    checked,
                                ) { on ->
                                    if (multiple) {
                                        draft = if (on) draft + option else draft - option
                                    } else {
                                        onSelect(setOf(option))
                                        onDismiss()
                                    }
                                },
                                testTag = testTag?.let { "$it$PICKER_OPTION_TAG${item.index}" },
                            ),
                            Modifier,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A select field and its [PickerSheet], for one value: the field shows [optionLabel] of [selected]
 * (or [placeholder]); a tap opens the sheet; picking calls [onSelect].
 */
@Composable
public fun <T> StapelSelect(
    label: String,
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    optionLabel: (T) -> String,
    dismissLabel: String = stapelString(CoreUiStrings.Close),
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    leadingIcon: StapelIcon? = null,
    searchLabel: String? = null,
    clearLabel: String? = stapelString(CoreUiStrings.Clear),
    noMatchesText: String? = null,
    key: ((T) -> Any)? = null,
    testTag: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    StapelSelectField(label, selected?.let(optionLabel), { open = true }, modifier, placeholder, supportingText, error, enabled, leadingIcon, testTag)
    if (open) {
        PickerSheet(
            title = label, options = options, optionLabel = optionLabel, selected = setOfNotNull(selected),
            onSelect = { picked -> picked.firstOrNull()?.let(onSelect) }, onDismiss = { open = false }, dismissLabel = dismissLabel,
            searchLabel = searchLabel, clearLabel = clearLabel, noMatchesText = noMatchesText, key = key,
            testTag = testTag?.plus(".sheet"),
        )
    }
}

/**
 * A select field and its [PickerSheet], for several values: the field shows [summary] of [selected]
 * (labels joined by commas by default); the sheet commits with [doneLabel].
 */
@Composable
public fun <T> StapelMultiSelect(
    label: String,
    options: List<T>,
    selected: Set<T>,
    onSelect: (Set<T>) -> Unit,
    optionLabel: (T) -> String,
    dismissLabel: String = stapelString(CoreUiStrings.Close),
    doneLabel: String = stapelString(CoreUiStrings.Done),
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    summary: (Set<T>) -> String? = { set -> set.takeIf { it.isNotEmpty() }?.joinToString(", ") { optionLabel(it) } },
    supportingText: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    searchLabel: String? = null,
    clearLabel: String? = stapelString(CoreUiStrings.Clear),
    noMatchesText: String? = null,
    key: ((T) -> Any)? = null,
    testTag: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    StapelSelectField(label, summary(selected), { open = true }, modifier, placeholder, supportingText, error, enabled, null, testTag)
    if (open) {
        PickerSheet(
            title = label, options = options, optionLabel = optionLabel, selected = selected, onSelect = onSelect,
            onDismiss = { open = false }, dismissLabel = dismissLabel, multiple = true, doneLabel = doneLabel,
            searchLabel = searchLabel, clearLabel = clearLabel, noMatchesText = noMatchesText, key = key,
            testTag = testTag?.plus(".sheet"),
        )
    }
}
