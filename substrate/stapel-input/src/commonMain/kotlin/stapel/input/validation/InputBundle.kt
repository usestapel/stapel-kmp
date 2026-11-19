package stapel.input.validation

import stapel.i18n.I18nBundle

/**
 * Floor copy for [InputIssueKeys] (`en`), registered like any bundle:
 * `StapelStrings { register(CoreUiBundle); register(InputBundle) }`. Apps and bricks add locales or
 * override entries; the validators themselves only ever return keys.
 */
public val InputBundle: I18nBundle = I18nBundle(
    namespace = "stapel.input",
    locales = mapOf(
        "en" to {
            mapOf(
                InputIssueKeys.REQUIRED to "Required",
                InputIssueKeys.TOO_SHORT to "At least {min} characters",
                InputIssueKeys.TOO_LONG to "At most {max} characters",
                InputIssueKeys.PATTERN to "Check the format",
                InputIssueKeys.BELOW_MIN to "Must be at least {min}",
                InputIssueKeys.ABOVE_MAX to "Must be at most {max}",
                InputIssueKeys.NOT_A_NUMBER to "Enter a number",
                InputIssueKeys.INCOMPLETE to "Not complete yet",
                InputIssueKeys.INVALID_DATE to "No such date",
                InputIssueKeys.CHECKSUM to "Check the number",
                InputIssueKeys.INVALID_PHONE to "Check the phone number",
            )
        },
    ),
)
