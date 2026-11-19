package stapel.result.text

import stapel.result.ErrorCode

/**
 * The floor: display text for the codes the client synthesizes (`stapel.http.*`,
 * `stapel.transport.failed`, `stapel.error.*`, `stapel.action.blocked.*`,
 * `stapel.contract.decode`) in en/ru/es.
 *
 * The sentences are deliberately generic and never carry a protocol number; the status goes to the
 * separate detail line. The table mirrors `@stapel/core`'s `coreErrors.ts`; the two keys it adds
 * (`stapel.error.detail_with_request`, `stapel.contract.decode`) exist because the native client
 * keeps the request id and decodes against generated models. Any catalog passed to
 * [ErrorTextResolver] overrides an entry here by defining the same key.
 */
public object ClientErrorCatalog : ErrorCatalog {
    override val owner: String get() = "client"

    /** Locales this catalog ships. */
    public val locales: Set<String> get() = BUNDLES.keys

    override fun spec(code: ErrorCode): ErrorSpec? = null

    override fun template(code: ErrorCode, locale: LocaleTag): String? = BUNDLES[locale.value]?.get(code.value)

    /** Every key of [locale] with its template (empty for a locale not shipped). */
    public fun entries(locale: String): Map<String, String> = BUNDLES[locale].orEmpty()

    private val BUNDLES: Map<String, Map<String, String>> = mapOf(
        "en" to mapOf(
            "stapel.error.unknown" to "Something went wrong. Please try again.",
            "stapel.contract.decode" to "Something went wrong. Please try again.",
            "stapel.transport.failed" to "Could not reach the server. Check your connection and try again.",
            "stapel.error.detail" to "HTTP {status}",
            "stapel.error.detail_with_request" to "HTTP {status} · {request_id}",
            "stapel.action.blocked.loading" to "Still loading. One moment.",
            "stapel.action.blocked.load_failed" to "We could not load what this needs. Reload the page to try again.",
            "stapel.http.4xx" to "That request could not be completed.",
            "stapel.http.400" to "That request could not be completed.",
            "stapel.http.401" to "Your session has expired. Please sign in again.",
            "stapel.http.403" to "You do not have access to this.",
            "stapel.http.404" to "This is no longer available.",
            "stapel.http.408" to "The server took too long to respond. Please try again.",
            "stapel.http.409" to "This changed while you were working. Please reload and try again.",
            "stapel.http.413" to "That is too large to upload.",
            "stapel.http.429" to "Too many requests. Please wait a moment and try again.",
            "stapel.http.5xx" to "Something went wrong on our side. Please try again in a moment.",
            "stapel.http.500" to "Something went wrong on our side. Please try again in a moment.",
            "stapel.http.502" to "Something went wrong on our side. Please try again in a moment.",
            "stapel.http.503" to "The service is temporarily unavailable. Please try again shortly.",
            "stapel.http.504" to "The server took too long to respond. Please try again.",
        ),
        "ru" to mapOf(
            "stapel.error.unknown" to "Что-то пошло не так. Попробуйте ещё раз.",
            "stapel.contract.decode" to "Что-то пошло не так. Попробуйте ещё раз.",
            "stapel.transport.failed" to "Не удалось связаться с сервером. Проверьте соединение и попробуйте ещё раз.",
            "stapel.error.detail" to "HTTP {status}",
            "stapel.error.detail_with_request" to "HTTP {status} · {request_id}",
            "stapel.action.blocked.loading" to "Ещё загружаем. Один момент.",
            "stapel.action.blocked.load_failed" to
                "Не удалось загрузить то, что нужно для этого действия. Обновите страницу и попробуйте ещё раз.",
            "stapel.http.4xx" to "Не удалось выполнить запрос.",
            "stapel.http.400" to "Не удалось выполнить запрос.",
            "stapel.http.401" to "Сессия истекла. Войдите снова.",
            "stapel.http.403" to "У вас нет доступа к этому.",
            "stapel.http.404" to "Это больше недоступно.",
            "stapel.http.408" to "Сервер слишком долго не отвечал. Попробуйте ещё раз.",
            "stapel.http.409" to "Данные изменились, пока вы работали. Обновите страницу и повторите.",
            "stapel.http.413" to "Слишком большой размер для загрузки.",
            "stapel.http.429" to "Слишком много запросов. Подождите немного и попробуйте снова.",
            "stapel.http.5xx" to "На нашей стороне произошла ошибка. Попробуйте ещё раз через минуту.",
            "stapel.http.500" to "На нашей стороне произошла ошибка. Попробуйте ещё раз через минуту.",
            "stapel.http.502" to "На нашей стороне произошла ошибка. Попробуйте ещё раз через минуту.",
            "stapel.http.503" to "Сервис временно недоступен. Попробуйте чуть позже.",
            "stapel.http.504" to "Сервер слишком долго не отвечал. Попробуйте ещё раз.",
        ),
        "es" to mapOf(
            "stapel.error.unknown" to "Algo salió mal. Inténtalo de nuevo.",
            "stapel.contract.decode" to "Algo salió mal. Inténtalo de nuevo.",
            "stapel.transport.failed" to "No se pudo conectar con el servidor. Comprueba tu conexión e inténtalo de nuevo.",
            "stapel.error.detail" to "HTTP {status}",
            "stapel.error.detail_with_request" to "HTTP {status} · {request_id}",
            "stapel.action.blocked.loading" to "Todavía se está cargando. Un momento.",
            "stapel.action.blocked.load_failed" to
                "No pudimos cargar lo que esta acción necesita. Recarga la página para intentarlo de nuevo.",
            "stapel.http.4xx" to "No se pudo completar la solicitud.",
            "stapel.http.400" to "No se pudo completar la solicitud.",
            "stapel.http.401" to "Tu sesión ha caducado. Inicia sesión de nuevo.",
            "stapel.http.403" to "No tienes acceso a esto.",
            "stapel.http.404" to "Esto ya no está disponible.",
            "stapel.http.408" to "El servidor tardó demasiado en responder. Inténtalo de nuevo.",
            "stapel.http.409" to "Esto cambió mientras trabajabas. Recarga la página e inténtalo de nuevo.",
            "stapel.http.413" to "Es demasiado grande para subirlo.",
            "stapel.http.429" to "Demasiadas solicitudes. Espera un momento e inténtalo de nuevo.",
            "stapel.http.5xx" to "Algo salió mal de nuestro lado. Inténtalo de nuevo en un momento.",
            "stapel.http.500" to "Algo salió mal de nuestro lado. Inténtalo de nuevo en un momento.",
            "stapel.http.502" to "Algo salió mal de nuestro lado. Inténtalo de nuevo en un momento.",
            "stapel.http.503" to "El servicio no está disponible temporalmente. Inténtalo de nuevo en breve.",
            "stapel.http.504" to "El servidor tardó demasiado en responder. Inténtalo de nuevo.",
        ),
    )
}
