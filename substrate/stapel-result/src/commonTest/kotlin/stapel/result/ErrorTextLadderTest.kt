package stapel.result

import stapel.result.text.ClientErrorCatalog
import stapel.result.text.ErrorCatalog
import stapel.result.text.ErrorSpec
import stapel.result.text.ErrorTextResolver
import stapel.result.text.LocaleTag
import stapel.result.text.interpolate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A hand-built catalog: `locale -> code -> template`. */
private class MapCatalog(
    private val bundles: Map<String, Map<String, String>>,
    override val owner: String = "test",
) : ErrorCatalog {
    override fun spec(code: ErrorCode): ErrorSpec? = null
    override fun template(code: ErrorCode, locale: LocaleTag): String? = bundles[locale.value]?.get(code.value)
}

private fun domain(
    code: String,
    status: Int = 400,
    params: Map<String, String> = emptyMap(),
    text: String? = null,
    language: String? = null,
    requestId: String? = null,
): StapelError = ServerRefusal(ErrorEnvelope(ErrorCode(code), status, ErrorParams(params), text, language, requestId))

private val en = LocaleTag("en")
private val ru = LocaleTag("ru")

/** Table-driven port of `formatFlowError.test.ts` and `coreErrorFloor.test.ts` (G-E1). */
class ErrorTextLadderTest {
    private val none = ErrorTextResolver(emptyList())

    @Test
    fun templateIsInterpolated() {
        val r = ErrorTextResolver(listOf(MapCatalog(mapOf("en" to mapOf("auth.otp.invalid" to "Code wrong, {attempts_left} left")))))
        assertEquals("Code wrong, 2 left", r.message(domain("auth.otp.invalid", params = mapOf("attempts_left" to "2")), en))
    }

    @Test
    fun serverTextOnlyWhenItsLanguageMatches() {
        val fr = domain("auth.otp.invalid", text = "Code invalide", language = "fr")
        assertEquals("Code invalide", none.message(fr, LocaleTag("fr")))
        assertEquals("Code invalide", none.message(fr, LocaleTag("fr-CA")), "a region does not change the language")
        assertEquals("auth.otp.invalid", none.message(fr, en))
        assertEquals("auth.otp.invalid", none.message(domain("auth.otp.invalid", text = "Some message"), en), "no language sent")
    }

    @Test
    fun rawCodeIsTheLastResort() {
        assertEquals("auth.otp.invalid", none.message(domain("auth.otp.invalid"), en))
    }

    @Test
    fun catalogBeatsServerTextEvenWhenLanguageMatches() {
        val r = ErrorTextResolver(listOf(MapCatalog(mapOf("en" to mapOf("auth.otp.invalid" to "Bundle text")))))
        assertEquals("Bundle text", r.message(domain("auth.otp.invalid", text = "Backend text", language = "en"), en))
    }

    @Test
    fun bodiless500ReadsHonestCopyWithStatusOnlyInDetail() {
        val e = TransportFailure.ServerFault(ErrorEnvelope.synthesized(500))
        val shown = none.describe(e, en)
        assertTrue("on our side" in shown.message)
        assertFalse("500" in shown.message)
        assertEquals("HTTP 500", shown.detail)
        assertTrue("На нашей стороне" in none.describe(e, ru).message)
        assertEquals("HTTP 500", none.describe(e, ru).detail)
    }

    @Test
    fun detailCarriesTheRequestIdWhenKnown() {
        val e = TransportFailure.ServerFault(ErrorEnvelope.synthesized(500, requestId = "req-7"))
        assertEquals("HTTP 500 · req-7", none.describe(e, en).detail)
    }

    @Test
    fun noDetailWhereNothingIsWorthQuoting() {
        val transport = none.describe(TransportFailure.Offline(), en)
        assertTrue("Could not reach the server" in transport.message)
        assertNull(transport.detail)
        assertNull(none.describe(TransportFailure.Unexpected("boom"), en).detail)
        val r = ErrorTextResolver(listOf(MapCatalog(mapOf("en" to mapOf("error.400.display_name_emoji" to "No emoji, please.")))))
        val specific = r.describe(domain("error.400.display_name_emoji", text = "no emoji"), en)
        assertEquals("No emoji, please.", specific.message)
        assertNull(specific.detail)
    }

    @Test
    fun hostOverridesTheDetailTemplateAndTheFloor() {
        val host = MapCatalog(mapOf("en" to mapOf("stapel.error.detail" to "code {status} / build 42", "stapel.http.500" to "Our bad.")))
        val r = ErrorTextResolver(listOf(host))
        val shown = r.describe(TransportFailure.ServerFault(ErrorEnvelope.synthesized(500)), en)
        assertEquals("code 500 / build 42", shown.detail)
        assertEquals("Our bad.", shown.message)
    }

    @Test
    fun regionalAndUnknownLocalesFallBack() {
        val e = TransportFailure.ServerFault(ErrorEnvelope.synthesized(500))
        assertEquals(none.message(e, ru), none.message(e, LocaleTag("ru-RU")))
        assertEquals(none.message(e, en), none.message(e, LocaleTag("fi")))
    }

    @Test
    fun onlyClientHttpCodesWidenToTheirClass() {
        val e507 = TransportFailure.ServerFault(ErrorEnvelope.synthesized(507))
        assertTrue("on our side" in none.message(e507, en))
        assertEquals("HTTP 507", none.describe(e507, en).detail)
        // A backend code without a status in its name is never widened.
        assertEquals("auth.meeting_not_found", none.message(domain("auth.meeting_not_found", status = 404), en))
    }

    @Test
    fun realBackendCodeBeatsTheStatusFallback() {
        val r = ErrorTextResolver(listOf(MapCatalog(mapOf("en" to mapOf("error.500.internal" to "The importer crashed.")))))
        assertEquals("The importer crashed.", r.message(domain("error.500.internal", status = 500, text = "boom"), en))
    }

    @Test
    fun untranslatedBackendCodeFallsToTheStatusFloorWithDetail() {
        val e = domain("error.503.service_unavailable", status = 503)
        for (locale in listOf("en", "ru", "es")) {
            val shown = none.describe(e, LocaleTag(locale))
            assertFalse("error.503" in shown.message, locale)
            assertEquals(ClientErrorCatalog.entries(locale)["stapel.http.503"], shown.message, locale)
            assertEquals("HTTP 503", shown.detail, locale)
        }
    }

    @Test
    fun translatedBackendCodeIsNeverWidenedAndQuotesNoStatus() {
        val r = ErrorTextResolver(listOf(MapCatalog(mapOf("en" to mapOf("error.503.service_unavailable" to "Search is being reindexed.")))))
        val shown = r.describe(domain("error.503.service_unavailable", status = 503), en)
        assertEquals("Search is being reindexed.", shown.message)
        assertNull(shown.detail)
    }

    @Test
    fun serverTextInTheHostLanguageBeatsTheFloor() {
        val e = domain("error.503.service_unavailable", status = 503, text = "Поиск переиндексируется.", language = "ru")
        assertEquals("Поиск переиндексируется.", none.message(e, ru))
        assertEquals(ClientErrorCatalog.entries("en")["stapel.http.503"], none.message(e, en))
    }

    @Test
    fun backendParamNamedStatusWinsOverTheResponseStatus() {
        val r = ErrorTextResolver(listOf(MapCatalog(mapOf("en" to mapOf("error.400.x" to "status={status}")))))
        assertEquals("status=400", r.message(domain("error.400.x"), en))
        assertEquals("status=pending", r.message(domain("error.400.x", params = mapOf("status" to "pending")), en))
    }

    @Test
    fun aLocaleSpecificEntryBeatsAnEnglishOneFromAnEarlierCatalog() {
        val pair = MapCatalog(mapOf("en" to mapOf("stapel.http.500" to "pair en")))
        assertEquals(ClientErrorCatalog.entries("ru")["stapel.http.500"], ErrorTextResolver(listOf(pair)).message(TransportFailure.ServerFault(ErrorEnvelope.synthesized(500)), ru))
    }

    @Test
    fun noSentenceCarriesAProtocolNumber() {
        for (locale in ClientErrorCatalog.locales) {
            for ((key, sentence) in ClientErrorCatalog.entries(locale)) {
                if (key.startsWith("stapel.error.detail")) continue
                assertFalse(Regex("""\d|\{status\}""").containsMatchIn(sentence), "$locale/$key reads a protocol number")
            }
        }
    }

    @Test
    fun everyLocaleHasTheSameKeys() {
        val keys = ClientErrorCatalog.entries("en").keys
        for (locale in ClientErrorCatalog.locales) assertEquals(keys, ClientErrorCatalog.entries(locale).keys, locale)
    }

    @Test
    fun everyCodeTheClientMintsHasFloorText() {
        val minted = listOf(
            TransportFailure.Offline(), TransportFailure.Timeout(TimeoutPhase.Socket), TransportFailure.ConnectionFailed(),
            TransportFailure.TlsRejected(pinMismatch = true), TransportFailure.Unauthenticated(),
            TransportFailure.ContractViolation("op", "bad"), TransportFailure.Unexpected("x"),
        ) + listOf(400, 401, 403, 404, 408, 409, 413, 418, 429, 500, 502, 503, 504, 599).map {
            classifyFailure(ErrorEnvelope.synthesized(it))!!
        }
        for (e in minted) {
            for (locale in ClientErrorCatalog.locales) {
                val text = none.message(e, LocaleTag(locale))
                assertFalse(text.startsWith("stapel."), "$e in $locale renders a raw key: $text")
            }
        }
    }

    @Test
    fun interpolateLeavesUnknownPlaceholders() {
        assertEquals("a 1 {b}", interpolate("a {a} {b}", mapOf("a" to "1")))
        assertEquals("x", interpolate("{p.q}", mapOf("p.q" to "x")))
    }

    @Test
    fun remediationFallsBackToTheCatalogSpecOnlyWhenUnknown() {
        val spec = ErrorSpec(ErrorCode("error.400.custom"), 400, emptyList(), Remediation.Verify, "x")
        val catalog = object : ErrorCatalog {
            override val owner = "t"
            override fun spec(code: ErrorCode) = spec.takeIf { code == it.code }
            override fun template(code: ErrorCode, locale: LocaleTag): String? = null
        }
        val unknown = object : StapelError by domain("error.400.custom") {
            override val remediation: Remediation get() = Remediation.Unknown
        }
        val r = ErrorTextResolver(listOf(catalog))
        assertEquals(Remediation.Verify, r.remediation(unknown))
        assertEquals(Remediation.FixInput, r.remediation(domain("error.400.custom")))
    }
}
