package stapel.ui.skin

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.text.input.ImeAction
import stapel.ui.DialogSurface
import stapel.ui.StapelIcon
import stapel.ui.StapelTone

/** Button emphasis. */
public enum class ButtonVariant {
    /** The main action: solid `brand`. */
    Primary,

    /** A secondary action: outlined. */
    Secondary,

    /** A low-emphasis action: text only. */
    Text,

    /** A destructive action: solid `error`. */
    Danger,
}

/** Control size. */
public enum class ControlSize {
    /** The layout's control height. */
    Regular,

    /** Compact (pointer layouts, dense rows). */
    Small,
}

/**
 * A button. Duties: one focusable, clickable node with `Role.Button`, carrying [testTag]; disabled
 * state when not [enabled]; while [loading] a second click is refused and the busy state announced;
 * with a [gatedReason] it looks unavailable, stays focusable, does not run [onClick], and shows and
 * announces the reason.
 */
@Immutable
public class ButtonSpec(
    /** Visible label and accessible name. */
    public val label: String,
    /** The action. */
    public val onClick: () -> Unit,
    /** Emphasis. */
    public val variant: ButtonVariant = ButtonVariant.Primary,
    /** Size. */
    public val size: ControlSize = ControlSize.Regular,
    /** False: disabled. */
    public val enabled: Boolean = true,
    /** True: busy; refuses a second click. */
    public val loading: Boolean = false,
    /** Non-null: the action is unavailable for this visible reason. */
    public val gatedReason: String? = null,
    /** Optional leading icon. */
    public val leadingIcon: StapelIcon? = null,
    /** Fill the available width. */
    public val fillWidth: Boolean = false,
    /** Test tag of the clickable node. */
    public val testTag: String? = null,
)

/** An icon-only button; [contentDescription] is required (its accessible name). */
@Immutable
public class IconButtonSpec(
    /** The glyph. */
    public val icon: StapelIcon,
    /** Accessible name. */
    public val contentDescription: String,
    /** The action. */
    public val onClick: () -> Unit,
    /** False: disabled. */
    public val enabled: Boolean = true,
    /** Tint tone. */
    public val tone: StapelTone = StapelTone.Neutral,
    /** Test tag of the clickable node. */
    public val testTag: String? = null,
)

/** What a text field holds (keyboard, autofill, obfuscation). */
public enum class TextFieldKind {
    /** Free text. */
    Text,

    /** An e-mail address. */
    Email,

    /** A phone number. */
    Phone,

    /** A password (obfuscated, with a show/hide toggle). */
    Password,

    /** A number. */
    Number,

    /** Several lines of text. */
    Multiline,

    /** A search query: search keyboard action, a clear action, a loading state. */
    Search,
}

/**
 * A text field. Duties: exactly one editable node bound to [state] (the skin never clamps or rewrites
 * the value; Password only obfuscates visually), [label] as its accessible name, [error] exposed as
 * the semantic error, [testTag] on the editable node. A [TextFieldKind.Search] field with text and a
 * [clearLabel] shows a clear button of that name which empties [state]; while [loading] it shows a
 * progress node and stays editable (typing is never blocked or debounced by the skin). The skin
 * forwards [inputTransformation] / [outputTransformation] to its state-based field and never formats
 * on its own; its keyboard comes from `TextFieldSpec.keyboardOptions()` (the transformation's keyboard
 * type, when it names one, wins over the one derived from [kind]).
 */
@Immutable
public class TextFieldSpec(
    /** The value. */
    public val state: TextFieldState,
    /** Visible label and accessible name. */
    public val label: String,
    /** Keyboard / obfuscation kind. */
    public val kind: TextFieldKind = TextFieldKind.Text,
    /** Hint shown while empty. */
    public val placeholder: String? = null,
    /** Helper text below the field. */
    public val supportingText: String? = null,
    /** Non-null: invalid, with this message (announced). */
    public val error: String? = null,
    /** False: disabled. */
    public val enabled: Boolean = true,
    /** True: selectable, not editable. */
    public val readOnly: Boolean = false,
    /** Optional leading icon. */
    public val leadingIcon: StapelIcon? = null,
    /** A unit shown after the value, never part of it. */
    public val suffix: String? = null,
    /** An action inside the field's end (e.g. clear). */
    public val trailingAction: IconButtonSpec? = null,
    /** Keyboard action. */
    public val imeAction: ImeAction = ImeAction.Default,
    /** Called on the keyboard action. */
    public val onImeAction: (() -> Unit)? = null,
    /** Autofill hint (Username, Password, NewPassword, EmailAddress, PhoneNumber, SmsOtpCode). */
    public val autofill: ContentType? = null,
    /** Minimum visible lines ([TextFieldKind.Multiline]). */
    public val minLines: Int = 1,
    /** Maximum visible lines ([TextFieldKind.Multiline]). */
    public val maxLines: Int = 1,
    /** Test tag of the editable node. */
    public val testTag: String? = null,
    /** Work behind the value is running (a search request): a progress node inside the field. */
    public val loading: Boolean = false,
    /** Accessible name of the clear action ([TextFieldKind.Search]); null draws none. */
    public val clearLabel: String? = null,
    /** Caller-supplied value transformation (e.g. an input mask); the skin forwards it and never formats on its own. */
    public val inputTransformation: InputTransformation? = null,
    /**
     * Caller-supplied display transformation (e.g. grouping separators); the skin forwards it and never
     * formats on its own. Not applied to [TextFieldKind.Password] (a secure field has none).
     */
    public val outputTransformation: OutputTransformation? = null,
)

/**
 * A fixed-length numeric code (OTP). Accepts digits only, up to [length]; calls [onComplete] once full.
 * A caller's [inputTransformation] runs before the skin's digit filter (it may fold non-ASCII digits or
 * pull the code out of a pasted message before the filter drops what is not an ASCII digit). The
 * digit cells read left to right in every layout direction (a code is a number).
 */
@Immutable
public class CodeFieldSpec(
    /** The value. */
    public val state: TextFieldState,
    /** Number of digits. */
    public val length: Int,
    /** Accessible name. */
    public val label: String,
    /** Non-null: invalid, with this message. */
    public val error: String? = null,
    /** False: disabled. */
    public val enabled: Boolean = true,
    /** Called with the code when [length] digits are entered. */
    public val onComplete: ((String) -> Unit)? = null,
    /** Test tag of the editable node. */
    public val testTag: String? = null,
    /** Caller-supplied value transformation, applied before the skin's digit filter; the skin never formats on its own. */
    public val inputTransformation: InputTransformation? = null,
)

/**
 * A dialog. Duties: content composed while shown; pane title from [title] or [accessibleName]; every
 * dismissal calls [onDismiss]; `dismissible = false` draws no dismiss affordance and refuses back and
 * outside taps.
 */
@Immutable
public class DialogSpec(
    /** Called on every dismissal. */
    public val onDismiss: () -> Unit,
    /** Sheet or modal: resolved by `stapel-ui` from the layout, not by the skin. */
    public val surface: DialogSurface,
    /** Visible title. */
    public val title: String? = null,
    /** Accessible name when there is no title. */
    public val accessibleName: String? = null,
    /** Label of the dismiss affordance (i18n copy from the caller). */
    public val dismissLabel: String,
    /** False: no dismiss affordance, back and outside taps refused. */
    public val dismissible: Boolean = true,
    /** Actions row. */
    public val footer: (@Composable () -> Unit)? = null,
    /** Test tag of the dialog pane. */
    public val testTag: String? = null,
)

/** An inline message (the `ErrorAlert` anatomy): polite live region. */
@Immutable
public class BannerSpec(
    /** Status tone. */
    public val tone: StapelTone,
    /** The sentence. */
    public val message: String,
    /** Optional title. */
    public val title: String? = null,
    /** Technical detail (`HTTP 503 · <request id>`), shown as a secondary line under the message. */
    public val detail: String? = null,
    /** Optional action. */
    public val action: ButtonSpec? = null,
    /** Non-null: dismissible. */
    public val onDismiss: (() -> Unit)? = null,
    /** Label of the dismiss affordance. */
    public val dismissLabel: String? = null,
    /** Test tag of the banner. */
    public val testTag: String? = null,
)

/** Progress presentation. */
public enum class ProgressKind {
    /** Indeterminate spinner. */
    Spinner,

    /** Bar (determinate with a fraction). */
    Linear,

    /** Placeholder shape while content loads ([ProgressSpec.skeleton] picks the shape). */
    Skeleton,
}

/** The shape of a skeleton placeholder. */
public enum class SkeletonShape {
    /** Text lines ([ProgressSpec.lines] of them, the last one shorter), at the body line height. */
    Text,

    /** A block filling the width; its height comes from the modifier (a card, an image), 96 dp by default. */
    Block,

    /** A circle (an avatar), 40 dp unless the modifier sizes it. */
    Circle,
}

/**
 * A progress indicator. Duties: a node with progress semantics (indeterminate, or [fraction]) named
 * [label]; a skeleton shimmers unless reduce motion is on (`LocalStapelReduceMotion`), when it is a
 * still placeholder.
 */
@Immutable
public class ProgressSpec(
    /** Presentation. */
    public val kind: ProgressKind,
    /** Accessible description ("Loading…"). */
    public val label: String? = null,
    /** 0..1 for determinate progress; null = indeterminate. */
    public val fraction: Float? = null,
    /** Test tag. */
    public val testTag: String? = null,
    /** Skeleton shape ([ProgressKind.Skeleton]). */
    public val skeleton: SkeletonShape = SkeletonShape.Text,
    /** Number of text lines ([SkeletonShape.Text]). */
    public val lines: Int = 1,
)

/** Surface fill. */
public enum class SurfaceTone {
    /** `surface`. */
    Plain,

    /** `surface-raised`. */
    Raised,

    /** `surface-sunken`. */
    Sunken,

    /** `surface-overlay`. */
    Overlay,
}

/** Elevation step of a surface. */
public enum class ElevationStep {
    /** No shadow. */
    None,

    /** Low. */
    Low,

    /** Medium. */
    Medium,

    /** High. */
    High,
}

/** A container (card, pane). */
@Immutable
public class SurfaceSpec(
    /** Fill. */
    public val tone: SurfaceTone = SurfaceTone.Raised,
    /** Shadow. */
    public val elevation: ElevationStep = ElevationStep.None,
    /** Draw a `border-subtle` outline. */
    public val bordered: Boolean = false,
    /** Non-null: the whole surface is one button. */
    public val onClick: (() -> Unit)? = null,
    /** Test tag. */
    public val testTag: String? = null,
)

/** What a list row shows at its start, as data (a skin draws it in its own style). */
public sealed interface ListItemMedia {
    /** A glyph, tinted by [tone] (neutral = muted text). */
    public class Icon(
        /** The glyph. */
        public val icon: StapelIcon,
        /** Tint tone. */
        public val tone: StapelTone = StapelTone.Neutral,
    ) : ListItemMedia

    /** An avatar ([AvatarSpec] keeps its own accessible name). */
    public class Avatar(
        /** The avatar. */
        public val spec: AvatarSpec,
    ) : ListItemMedia
}

/**
 * The value block at a row's end: a [text] line and an optional [supporting] line under it (an
 * amount and its date, a status and its time). It is laid out in full — never ellipsized — and the
 * headline column wraps instead of running under it.
 */
@Immutable
public class ListItemValue(
    /** The value line. */
    public val text: String,
    /** A second, quieter line under the value. */
    public val supporting: String? = null,
    /** Tone of the value line (neutral = normal text). */
    public val tone: StapelTone = StapelTone.Neutral,
)

/** What a list row shows at its very end, as data. */
public sealed interface ListItemAccessory {
    /** A disclosure chevron: the row opens something. */
    public data object Chevron : ListItemAccessory

    /** A badge (a count or a dot). */
    public class Badge(
        /** The badge. */
        public val spec: BadgeSpec,
    ) : ListItemAccessory

    /**
     * A two-state control: the whole row becomes that control (one toggleable node with the
     * headline as its label); the row's `onClick` is not used.
     */
    public class Toggle(
        /** Switch, checkbox or radio. */
        public val kind: ToggleKind,
        /** State. */
        public val checked: Boolean,
        /** Called with the new state (a radio is only ever turned on). */
        public val onCheckedChange: (Boolean) -> Unit,
    ) : ListItemAccessory
}

/**
 * A list row. Duties: the headline and supporting lines are text of the row's node; a clickable row
 * is one node with `Role.Button` (and `Selected` when [selected]); a [ListItemAccessory.Toggle] row is
 * one toggleable node with the toggle's role, flipped by tapping anywhere on it; a disabled row
 * refuses clicks; the row is at least the touch-target tall; the [value] block is laid out in full and
 * never overlaps the headline; the headline and supporting lines wrap (a caller may cap them with
 * [headlineMaxLines] / [supportingMaxLines], then they ellipsize) — at 200 % text nothing is clipped.
 */
@Immutable
public class ListItemSpec(
    /** Main line. */
    public val headline: String,
    /** Second line. */
    public val supporting: String? = null,
    /** Line above the headline. */
    public val overline: String? = null,
    /** Custom leading content; prefer [media] (data a skin can restyle). */
    public val leading: (@Composable () -> Unit)? = null,
    /** Custom trailing content; prefer [value] and [accessory]. */
    public val trailing: (@Composable () -> Unit)? = null,
    /** Non-null: the row is clickable. */
    public val onClick: (() -> Unit)? = null,
    /** Selected state. */
    public val selected: Boolean = false,
    /** False: disabled. */
    public val enabled: Boolean = true,
    /** Test tag. */
    public val testTag: String? = null,
    /** Leading icon or avatar. */
    public val media: ListItemMedia? = null,
    /** The two-line value block at the end. */
    public val value: ListItemValue? = null,
    /** Chevron, badge or toggle after the value. */
    public val accessory: ListItemAccessory? = null,
    /** Lines the headline may wrap to before it ellipsizes (unlimited by default: large text wraps, never clips). */
    public val headlineMaxLines: Int = Int.MAX_VALUE,
    /** Lines the supporting text may wrap to before it ellipsizes (unlimited by default). */
    public val supportingMaxLines: Int = Int.MAX_VALUE,
)

/**
 * A screen's top bar. Duties: the title is the one heading of the bar and is never truncated at large
 * text (it wraps); [subtitle] is visible text under it; with [collapsedFraction] the bar is the large
 * variant — at 0 a large title block under the bar, shrinking with the fraction, at 1 only the bar —
 * and the title stays the one heading throughout.
 */
@Immutable
public class TopBarSpec(
    /** Title (a heading). */
    public val title: String,
    /** Navigation button (back / close). */
    public val navigation: IconButtonSpec? = null,
    /** Action buttons (three at most fit a phone bar). */
    public val actions: List<IconButtonSpec> = emptyList(),
    /** Test tag. */
    public val testTag: String? = null,
    /** A line under the title (a count, a status). */
    public val subtitle: String? = null,
    /** Null: a regular bar. 0..1: the large variant, collapsed by this fraction (`StapelPage` drives it from the scroll). */
    public val collapsedFraction: Float? = null,
)

/** One transient message. */
@Immutable
public class SnackbarSpec(
    /** The message. */
    public val message: String,
    /** Tone. */
    public val tone: StapelTone = StapelTone.Neutral,
    /** Optional action label. */
    public val actionLabel: String? = null,
    /** The action. */
    public val onAction: (() -> Unit)? = null,
    /** Called when dismissed. */
    public val onDismiss: (() -> Unit)? = null,
    /** Test tag. */
    public val testTag: String? = null,
)

/** Toggle presentation. */
public enum class ToggleKind {
    /** On/off switch. */
    Switch,

    /** Checkbox (may be indeterminate). */
    Checkbox,

    /** Radio button (one of a group): tapping a checked radio keeps it checked. */
    Radio,
}

/**
 * A two-state control with its label. Duties: one node carrying the label text, the role of [kind]
 * (`Switch`, `Checkbox`, `RadioButton`); a switch or checkbox exposes its toggle state (`On`, `Off`,
 * `Indeterminate` when [indeterminate]) and flips through [onCheckedChange]; a radio exposes
 * `Selected` and only ever reports `true`; [supporting] is visible text; [error] is the semantic error;
 * disabled refuses taps; the node is at least the touch target.
 */
@Immutable
public class ToggleSpec(
    /** Presentation. */
    public val kind: ToggleKind,
    /** State. */
    public val checked: Boolean,
    /** Called with the new state. */
    public val onCheckedChange: (Boolean) -> Unit,
    /** Visible label and accessible name. */
    public val label: String,
    /** False: disabled. */
    public val enabled: Boolean = true,
    /** Test tag. */
    public val testTag: String? = null,
    /** A quieter line under the label. */
    public val supporting: String? = null,
    /** Checkbox only: neither on nor off (a partly selected group); a tap reports `true`. */
    public val indeterminate: Boolean = false,
    /** Non-null: invalid, with this message (announced). */
    public val error: String? = null,
    /** True: label first and the control at the row's end (settings rows); false: control first. */
    public val controlAtEnd: Boolean = false,
)

/** Tabs presentation. */
public enum class TabsVariant {
    /** Underlined tabs. */
    Tabs,

    /** Segmented control. */
    Segmented,
}

/**
 * A set of tabs. Duties: every item is a node with `Role.Tab` and `Selected` state, at least the touch
 * target, carrying its label as text (or as its accessible name when [iconOnly]); a tap calls
 * [onSelect] with its index; with [scrollable] every item stays reachable however many there are.
 */
@Immutable
public class TabsSpec(
    /** Labels (always required: they are the accessible names of icon-only items). */
    public val items: List<String>,
    /** Selected index. */
    public val selectedIndex: Int,
    /** Called with the picked index. */
    public val onSelect: (Int) -> Unit,
    /** Presentation. */
    public val variant: TabsVariant = TabsVariant.Tabs,
    /** Test tag. */
    public val testTag: String? = null,
    /** One glyph per item (same size as [items]), or null for text-only items. */
    public val icons: List<StapelIcon>? = null,
    /** Draw only the glyphs; the labels become accessible names. Needs [icons]. */
    public val iconOnly: Boolean = false,
    /** Scroll horizontally instead of sharing the width (many or long labels). */
    public val scrollable: Boolean = false,
)

/** Chip kind. */
public enum class ChipKind {
    /** Single choice among chips. */
    Choice,

    /** A filter on/off. */
    Filter,

    /** A read-only status tag. */
    Status,

    /** An entered value (a recipient, a tag) that can be removed. */
    Input,
}

/**
 * A chip. Duties: a [ChipKind.Choice] / [ChipKind.Filter] chip with [onClick] is one node with
 * `Selected` state, at least the touch target; a [ChipKind.Status] chip is plain text with no click
 * action; with [onRemove] a separate remove button named [removeLabel] calls [onRemove] (never
 * [onClick]); disabled refuses both; the label is never clipped (the chip grows with large text).
 */
@Immutable
public class ChipSpec(
    /** Label. */
    public val label: String,
    /** Kind. */
    public val kind: ChipKind = ChipKind.Choice,
    /** Selected ([ChipKind.Choice], [ChipKind.Filter]). */
    public val selected: Boolean = false,
    /** Non-null: clickable. */
    public val onClick: (() -> Unit)? = null,
    /** Tone ([ChipKind.Status]). */
    public val tone: StapelTone = StapelTone.Neutral,
    /** Test tag. */
    public val testTag: String? = null,
    /** Optional leading glyph. */
    public val leadingIcon: StapelIcon? = null,
    /** Non-null: removable; called by the remove button. */
    public val onRemove: (() -> Unit)? = null,
    /** Accessible name of the remove button (i18n copy from the caller); the dismiss string by default. */
    public val removeLabel: String? = null,
    /** False: disabled. */
    public val enabled: Boolean = true,
    /** Size. */
    public val size: ControlSize = ControlSize.Regular,
)

/** Avatar size. */
public enum class AvatarSize(
    /** Diameter in dp. */
    public val dp: Int,
) {
    /** Dense rows, chips: 32. */
    Small(32),

    /** List rows: 40. */
    Medium(40),

    /** Headers: 56. */
    Large(56),

    /** Profile pages: 80. */
    XLarge(80),
}

/** Where a badge sits on an avatar. */
public enum class BadgePlacement {
    /** Top end corner (counts). */
    TopEnd,

    /** Bottom end corner (presence, status). */
    BottomEnd,
}

/**
 * An avatar. Content ladder: the [image] when the theme's image resolver draws it, else the
 * [initials], else the [icon], else a generic person glyph — so a missing or failing image never
 * leaves a blank circle. Duties: one node whose accessible name is [contentDescription] (plus the
 * badge's description), exactly [size] in diameter; with [onClick] a button of at least the touch
 * target.
 */
@Immutable
public class AvatarSpec(
    /** One or two letters (see `avatarInitials`); null or blank: skip to [icon]. */
    public val initials: String?,
    /** Accessible name (the person's or entity's name). */
    public val contentDescription: String,
    /** Diameter. */
    public val size: AvatarSize = AvatarSize.Medium,
    /** Glyph when there are no initials. */
    public val icon: StapelIcon? = null,
    /** An image source handed to `LocalStapelImages` (a URL, a content id). */
    public val image: String? = null,
    /** A count or a status dot on the avatar. */
    public val badge: BadgeSpec? = null,
    /** Where [badge] sits. */
    public val badgePlacement: BadgePlacement = BadgePlacement.BottomEnd,
    /** Non-null: the avatar is a button. */
    public val onClick: (() -> Unit)? = null,
    /** Test tag. */
    public val testTag: String? = null,
)

/** Badge size. */
public enum class BadgeSize {
    /** A 6 dp dot / caption count. */
    Small,

    /** The default: 8 dp dot / 16 dp count. */
    Medium,

    /** A 10 dp dot / 20 dp count. */
    Large,
}

/** Badge fill. */
public enum class BadgeEmphasis {
    /** Solid tone fill with the tone's on-colour. */
    Solid,

    /** Tinted tone background with the tone's text colour (calmer; for dense lists). */
    Subtle,
}

/**
 * A badge: a count, or a dot when [count] is null. Duties: a count above [max] reads `max+`; a count
 * of zero or less draws nothing (the content stays); a dot has no text; [contentDescription] is the
 * badge node's accessible description.
 */
@Immutable
public class BadgeSpec(
    /** The count; null draws a dot. */
    public val count: Int? = null,
    /** Accessible description ("3 unread"). */
    public val contentDescription: String? = null,
    /** Test tag. */
    public val testTag: String? = null,
    /** Tone ([StapelTone.Error] = attention, the default). */
    public val tone: StapelTone = StapelTone.Error,
    /** Size. */
    public val size: BadgeSize = BadgeSize.Medium,
    /** Fill. */
    public val emphasis: BadgeEmphasis = BadgeEmphasis.Solid,
    /** The largest count shown as a number. */
    public val max: Int = 99,
    /** Optional glyph before the count. */
    public val icon: StapelIcon? = null,
)

/**
 * A select field: shows the chosen value and opens a picker (`PickerSheet`) — it never edits text.
 * Duties: one clickable node with `Role.DropdownList`, named by [label], whose text carries [value]
 * (or [placeholder] when nothing is chosen); [error] is the semantic error; disabled refuses taps; at
 * least the touch target; the value wraps instead of being clipped at large text.
 */
@Immutable
public class SelectSpec(
    /** Visible label and accessible name. */
    public val label: String,
    /** The chosen value's text; null when nothing is chosen. */
    public val value: String?,
    /** Opens the picker. */
    public val onClick: () -> Unit,
    /** Shown while nothing is chosen. */
    public val placeholder: String? = null,
    /** Helper text below the field. */
    public val supportingText: String? = null,
    /** Non-null: invalid, with this message (announced). */
    public val error: String? = null,
    /** False: disabled. */
    public val enabled: Boolean = true,
    /** Optional leading glyph. */
    public val leadingIcon: StapelIcon? = null,
    /** Test tag of the clickable node. */
    public val testTag: String? = null,
)

/**
 * Dots under a pager or carousel. Duties: one node whose state description is [description] ("Page 2
 * of 5", i18n copy from the caller) and whose text is never drawn; at most [maxDots] dots, a window
 * that keeps [current] visible (edge dots smaller when the window is cut, see `pagerDotWindow`); with
 * [onSelect] every visible dot is a button of at least the touch target named by [dotLabel].
 */
@Immutable
public class PagerIndicatorSpec(
    /** Number of pages. */
    public val count: Int,
    /** Current page, 0-based. */
    public val current: Int,
    /** Accessible state ("Page 2 of 5"). */
    public val description: String,
    /** Non-null: tapping a dot jumps to its page. */
    public val onSelect: ((Int) -> Unit)? = null,
    /** Accessible name of the dot for a page (0-based), when [onSelect] is set ("Go to page 3"). */
    public val dotLabel: ((Int) -> String)? = null,
    /** Most dots drawn at once. */
    public val maxDots: Int = 7,
    /** Test tag. */
    public val testTag: String? = null,
)

/** One action of a [MenuSpec]. */
@Immutable
public class MenuItemSpec(
    /** Visible label and accessible name. */
    public val label: String,
    /** Runs the action; the menu then closes ([MenuSpec.onDismiss]). */
    public val onClick: () -> Unit,
    /** Optional leading glyph. */
    public val icon: StapelIcon? = null,
    /** False: shown, but refuses taps. */
    public val enabled: Boolean = true,
    /** A destructive action (delete, sign out): drawn in the error role. */
    public val destructive: Boolean = false,
    /** Test tag of the item. */
    public val testTag: String? = null,
)

/**
 * An action menu (an overflow "more" menu, a row's actions) anchored to the content the slot draws
 * first. Not a picker: choosing a value is `Select` + `PickerSheet`. Duties: while [expanded] the menu
 * is a pane titled [label] with a dismiss action, drawn over the content at the anchor (overlapping
 * its horizontal extent, below it, above when there is no room below) and inside the window; every
 * item is a node with `Role.Button` whose text is its label, at least the touch target tall; a tap
 * runs the item's `onClick` and then [onDismiss] once; a disabled item refuses taps; the dismiss action
 * (and the platform's outside tap, back, Escape) calls [onDismiss]; collapsed, no item node exists.
 */
@Immutable
public class MenuSpec(
    /** The menu is open. */
    public val expanded: Boolean,
    /** Closes the menu (the caller sets [expanded] to false). */
    public val onDismiss: () -> Unit,
    /** The actions, in order. */
    public val items: List<MenuItemSpec>,
    /** Accessible title of the menu pane ("Message actions"; i18n copy from the caller). */
    public val label: String,
    /** Test tag of the menu pane. */
    public val testTag: String? = null,
)

/**
 * A quantity stepper: a decrement button, the value, an increment button. Duties: the container is
 * one node named [label] whose state description is the shown value; the buttons are `Role.Button`
 * nodes named [decrementLabel] / [incrementLabel], at least the touch target; a tap calls
 * [onValueChange] once with the value moved by [step] and clamped to [min]..[max] (`stepperValue`); at
 * [min] the decrement is disabled, at [max] the increment; disabled refuses both; the value text is
 * never clipped (it grows with large text).
 */
@Immutable
public class StepperSpec(
    /** The current value. */
    public val value: Int,
    /** Called with the new value. */
    public val onValueChange: (Int) -> Unit,
    /** Accessible name of the stepper ("Quantity"). */
    public val label: String,
    /** Accessible name of the decrement button ("Remove one"). */
    public val decrementLabel: String,
    /** Accessible name of the increment button ("Add one"). */
    public val incrementLabel: String,
    /** Smallest value. */
    public val min: Int = 0,
    /** Largest value. */
    public val max: Int = Int.MAX_VALUE,
    /** Amount one tap moves the value. */
    public val step: Int = 1,
    /** The shown text of the value (a formatted amount); `value.toString()` when null. */
    public val valueText: String? = null,
    /** False: disabled. */
    public val enabled: Boolean = true,
    /** Test tag of the container. */
    public val testTag: String? = null,
) {
    init {
        require(min <= max) { "StepperSpec: min $min > max $max" }
        require(step > 0) { "StepperSpec: step must be positive, was $step" }
    }
}

/** How a [ChipGroupSpec] arranges its chips. */
public enum class ChipGroupLayout {
    /** Rows that wrap onto new lines (filters above a list). */
    Wrap,

    /** One row that scrolls horizontally (a category strip). */
    Scroll,
}

/**
 * A group of chips. Duties: the group is one node named [label] (a selectable group); every chip is
 * drawn through the theme skin's `Chip` slot (so a project's chip applies) with its own duties;
 * [ChipGroupLayout.Wrap] keeps every chip inside the group's width on as many rows as needed;
 * [ChipGroupLayout.Scroll] keeps one row that scrolls horizontally, every chip reachable.
 */
@Immutable
public class ChipGroupSpec(
    /** The chips, in order (selection state lives in each chip). */
    public val chips: List<ChipSpec>,
    /** Accessible name of the group ("Filters"). */
    public val label: String,
    /** Arrangement. */
    public val layout: ChipGroupLayout = ChipGroupLayout.Wrap,
    /** Test tag of the group. */
    public val testTag: String? = null,
)

/** One destination of the primary navigation. */
@Immutable
public class NavigationItemSpec(
    /** Stable id. */
    public val id: String,
    /** Label. */
    public val label: String,
    /** Glyph. */
    public val icon: StapelIcon,
    /** Optional badge. */
    public val badge: BadgeSpec? = null,
)

/**
 * The primary navigation (bar on a phone, rail on wider windows). Duties: every item is a node with
 * `Role.Tab`, `Selected` for [selectedId], its label as text; a tap calls [onSelect] with its id.
 */
@Immutable
public class NavigationSpec(
    /** Destinations. */
    public val items: List<NavigationItemSpec>,
    /** Selected destination id. */
    public val selectedId: String?,
    /** Called with the picked id. */
    public val onSelect: (String) -> Unit,
    /** Test tag. */
    public val testTag: String? = null,
)

/**
 * Pull-to-refresh around scrollable content. Duties: the content is drawn; while [refreshing] a progress
 * node (indeterminate, named [label]) is shown; when not refreshing, the refresh is reachable without
 * a gesture — an accessibility action named [label] on the container, and for pointer input a visible
 * control — and a pull past the threshold on touch calls [onRefresh] once.
 */
@Immutable
public class PullToRefreshSpec(
    /** A refresh is running. */
    public val refreshing: Boolean,
    /** Starts a refresh. */
    public val onRefresh: () -> Unit,
    /** Accessible label of the refresh action. */
    public val label: String,
    /** Test tag. */
    public val testTag: String? = null,
)

/**
 * A brick-local widget's key (a QR canvas, a waveform): the brick ships [default]; a skin may return
 * its own value from [StapelSkin.extension].
 *
 * @property name unique name (`auth.qr`).
 * @property default the brick's own implementation.
 */
public class SkinExtensionKey<T : Any>(public val name: String, public val default: T)
