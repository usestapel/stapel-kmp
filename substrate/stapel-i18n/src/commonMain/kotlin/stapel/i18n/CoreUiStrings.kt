package stapel.i18n

/** Typed keys of the `stapel.ui.*` floor (the copy of `stapel-ui`'s composites and skins). */
public object CoreUiStrings {
    /** "Try again". */
    public val Retry: StringKey = StringKey("stapel.ui.retry")

    /** "Dismiss". */
    public val Dismiss: StringKey = StringKey("stapel.ui.dismiss")

    /** "Close". */
    public val Close: StringKey = StringKey("stapel.ui.close")

    /** "Back". */
    public val Back: StringKey = StringKey("stapel.ui.back")

    /** "Confirm". */
    public val Confirm: StringKey = StringKey("stapel.ui.confirm")

    /** "Cancel". */
    public val Cancel: StringKey = StringKey("stapel.ui.cancel")

    /** "Loading…". */
    public val Loading: StringKey = StringKey("stapel.ui.loading")

    /** "Nothing here yet". */
    public val EmptyTitle: StringKey = StringKey("stapel.ui.empty_title")

    /** "Show password". */
    public val ShowPassword: StringKey = StringKey("stapel.ui.show_password")

    /** "Hide password". */
    public val HidePassword: StringKey = StringKey("stapel.ui.hide_password")

    /** "Details" (the collapsed technical line of an error banner). */
    public val Details: StringKey = StringKey("stapel.ui.details")

    /** "{count} characters left" — a plural family. */
    public val CharactersLeft: PluralKey = PluralKey("stapel.ui.characters_left")
}

/** The `stapel.ui.*` floor bundle in en, ru and es. Register it first. */
public val CoreUiBundle: I18nBundle = I18nBundle(
    namespace = "stapel.ui",
    locales = mapOf(
        "en" to {
            mapOf(
                "stapel.ui.retry" to "Try again",
                "stapel.ui.dismiss" to "Dismiss",
                "stapel.ui.close" to "Close",
                "stapel.ui.back" to "Back",
                "stapel.ui.confirm" to "Confirm",
                "stapel.ui.cancel" to "Cancel",
                "stapel.ui.loading" to "Loading…",
                "stapel.ui.empty_title" to "Nothing here yet",
                "stapel.ui.show_password" to "Show password",
                "stapel.ui.hide_password" to "Hide password",
                "stapel.ui.details" to "Details",
                "stapel.ui.characters_left.one" to "{count} character left",
                "stapel.ui.characters_left.other" to "{count} characters left",
            )
        },
        "ru" to {
            mapOf(
                "stapel.ui.retry" to "Повторить",
                "stapel.ui.dismiss" to "Скрыть",
                "stapel.ui.close" to "Закрыть",
                "stapel.ui.back" to "Назад",
                "stapel.ui.confirm" to "Подтвердить",
                "stapel.ui.cancel" to "Отмена",
                "stapel.ui.loading" to "Загрузка…",
                "stapel.ui.empty_title" to "Здесь пока пусто",
                "stapel.ui.show_password" to "Показать пароль",
                "stapel.ui.hide_password" to "Скрыть пароль",
                "stapel.ui.details" to "Подробнее",
                "stapel.ui.characters_left.one" to "Остался {count} символ",
                "stapel.ui.characters_left.few" to "Осталось {count} символа",
                "stapel.ui.characters_left.many" to "Осталось {count} символов",
                "stapel.ui.characters_left.other" to "Осталось {count} символа",
            )
        },
        "es" to {
            mapOf(
                "stapel.ui.retry" to "Reintentar",
                "stapel.ui.dismiss" to "Descartar",
                "stapel.ui.close" to "Cerrar",
                "stapel.ui.back" to "Atrás",
                "stapel.ui.confirm" to "Confirmar",
                "stapel.ui.cancel" to "Cancelar",
                "stapel.ui.loading" to "Cargando…",
                "stapel.ui.empty_title" to "Aún no hay nada aquí",
                "stapel.ui.show_password" to "Mostrar contraseña",
                "stapel.ui.hide_password" to "Ocultar contraseña",
                "stapel.ui.details" to "Detalles",
                "stapel.ui.characters_left.one" to "Queda {count} carácter",
                "stapel.ui.characters_left.many" to "Quedan {count} de caracteres",
                "stapel.ui.characters_left.other" to "Quedan {count} caracteres",
            )
        },
    ),
)
