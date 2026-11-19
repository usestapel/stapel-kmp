package stapel.ui.skin

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
}

/**
 * A text field. Duties: exactly one editable node bound to [state] (the skin never clamps or rewrites
 * the value; Password only obfuscates visually), [label] as its accessible name, [error] exposed as
 * the semantic error, [testTag] on the editable node.
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
)

/** A fixed-length numeric code (OTP). Accepts digits only, up to [length]; calls [onComplete] once full. */
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

    /** Placeholder block while content loads. */
    Skeleton,
}

/** A progress indicator. */
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

/** A list row. */
@Immutable
public class ListItemSpec(
    /** Main line. */
    public val headline: String,
    /** Second line. */
    public val supporting: String? = null,
    /** Line above the headline. */
    public val overline: String? = null,
    /** Leading content (icon, avatar). */
    public val leading: (@Composable () -> Unit)? = null,
    /** Trailing content (switch, chevron, value). */
    public val trailing: (@Composable () -> Unit)? = null,
    /** Non-null: the row is clickable. */
    public val onClick: (() -> Unit)? = null,
    /** Selected state. */
    public val selected: Boolean = false,
    /** False: disabled. */
    public val enabled: Boolean = true,
    /** Test tag. */
    public val testTag: String? = null,
)

/** A screen's top bar. */
@Immutable
public class TopBarSpec(
    /** Title (a heading). */
    public val title: String,
    /** Navigation button (back / close). */
    public val navigation: IconButtonSpec? = null,
    /** Action buttons. */
    public val actions: List<IconButtonSpec> = emptyList(),
    /** Test tag. */
    public val testTag: String? = null,
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

    /** Checkbox. */
    Checkbox,

    /** Radio button (one of a group). */
    Radio,
}

/** A two-state control with its label. */
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
)

/** Tabs presentation. */
public enum class TabsVariant {
    /** Underlined tabs. */
    Tabs,

    /** Segmented control. */
    Segmented,
}

/** A set of tabs. */
@Immutable
public class TabsSpec(
    /** Labels. */
    public val items: List<String>,
    /** Selected index. */
    public val selectedIndex: Int,
    /** Called with the picked index. */
    public val onSelect: (Int) -> Unit,
    /** Presentation. */
    public val variant: TabsVariant = TabsVariant.Tabs,
    /** Test tag. */
    public val testTag: String? = null,
)

/** Chip kind. */
public enum class ChipKind {
    /** Single choice among chips. */
    Choice,

    /** A filter on/off. */
    Filter,

    /** A read-only status tag. */
    Status,
}

/** A chip. */
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
)

/** An avatar: initials (an image source arrives with `stapel-image-compose`). */
@Immutable
public class AvatarSpec(
    /** One or two letters. */
    public val initials: String,
    /** Accessible name. */
    public val contentDescription: String,
    /** Diameter in dp. */
    public val sizeDp: Int = 40,
    /** Test tag. */
    public val testTag: String? = null,
)

/** A badge: a count, or a dot when [count] is null. */
@Immutable
public class BadgeSpec(
    /** The count; null draws a dot. */
    public val count: Int? = null,
    /** Accessible description ("3 unread"). */
    public val contentDescription: String? = null,
    /** Test tag. */
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

/** The primary navigation (bar on a phone, rail on wider windows). */
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

/** Pull-to-refresh around scrollable content. */
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
