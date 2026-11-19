package stapel.tools.codegen

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneratorTest {
    private val constructs: Path = Paths.get(javaClass.getResource("/fixtures/constructs/schema.json")!!.toURI()).parent
    private val goldenRoot: Path = Fixtures.repoRoot.resolve("tools/codegen/core/src/test/resources/golden/constructs")

    /**
     * Golden tree of the constructs fixture (one mini-contract holding every construct of §5).
     * `UPDATE_GOLDEN=1 ./gradlew :tools:codegen:core:test` rewrites it; the diff is the review.
     */
    @Test
    fun constructsMatchTheGoldenTree() {
        val produced = Generator("constructs", constructs).run().files
        if (System.getProperty("stapel.updateGolden") == "1") {
            goldenRoot.toFile().deleteRecursively()
            produced.forEach { (p, t) -> goldenRoot.resolve("$p.golden").also { Files.createDirectories(it.parent) }.let { Files.writeString(it, t) } }
        }
        // Stored as `.golden` so the package-root gate does not read them as sources of this module.
        val golden = Generator.existingFiles(goldenRoot).mapKeys { it.key.removeSuffix(".golden") }
        assertEquals(emptyList(), Generator.diff(golden, produced))
    }

    @Test
    fun constructsAreMappedPerTheRules() {
        val files = Generator("constructs", constructs).run().files
        fun file(name: String) = files.entries.single { it.key.endsWith("/$name.kt") }.value
        val item = file("Item")
        assertTrue("@SerialName(\"object\")\n    public val object_: Boolean? = null" in item, "keyword property")
        assertTrue("public val price: Decimal," in item, "decimal is never Double")
        assertTrue("public val createdAt: Instant," in item)
        assertTrue("public val note: String?," in item, "required nullable has no default")
        assertTrue("@Serializable(with = StatusBlankOrNull::class)" in item, "blank/null enum union")
        assertTrue("public val meta: Map<String, Int>? = null" in item)
        assertTrue("public val parent: Ref? = null" in item, "single-allOf wrapper is transparent")
        val request = file("ItemRequest")
        assertFalse("createdAt" in request, "readOnly dropped from the request twin")
        assertTrue("NAME_MAX_LENGTH: Int = 40" in request && "NAME_PATTERN" in request, "constraint constants on request models")
        assertTrue("public val note: Maybe<String?> = Maybe.Absent" in file("PatchedItem"))
        val outcome = file("Outcome")
        assertTrue("\"DONE\", \"MERGED\" -> Done.serializer()" in outcome && "\"PENDING\" -> Pending.serializer()" in outcome, "selector by wire values")
        assertTrue("\"ref_id\" in obj -> Ref.serializer()" in file("ByKeys"), "key probe union")
        assertTrue("public val label: String" in file("Merged") && "public val refId: Int" in file("Merged"), "allOf merge")
        val page = file("ItemPage")
        assertTrue(": Page<Item>" in page && "if (hasNext) nextAnchor else null" in page, "anchor envelope implements Page")
        val api = file("ConstructsApi")
        assertTrue("suspend fun <R> exportRetrieve" in api.replace("getExport", "exportRetrieve") || "suspend fun <R> getExport(" in api, "binary download")
        assertTrue("suspend fun getFeed(): StapelResult<String, GetFeedError>" in api, "text body")
        assertTrue("onProgress: ((sent: Long, total: Long?) -> Unit)? = null" in api, "multipart upload progress")
        assertTrue("fun getOauthUrl(provider: String): String" in file("ConstructsUrls"), "3xx-only operation becomes a URL")
        assertFalse(files.keys.any { "InternalHook" in it }, "service audience is not emitted")
        assertTrue("sealed interface CreateItemsError : ConstructsApiError" in file("CreateItemsError"))
        assertTrue("typealias DeleteItemsError = ConstructsApiError" in file("DeleteItemsError"))
        assertTrue("captcha = CaptchaRequirement(\"create_item\", \"X-Captcha-Token\", \"captcha_token\")" in file("ConstructsOperations"))
        assertTrue("fun ConstructsApi.listItemsPager(" in file("ConstructsPagers"))
        assertTrue("public val maxLength: Int?" in file("ConstructsApiError"), "typed error params")
    }

    @Test
    fun generationIsDeterministic() {
        val a = Generator("constructs", constructs).run().files
        val b = Generator("constructs", constructs).run().files
        assertEquals(a, b)
        val auth = Fixtures.repoRoot.resolve("contracts/auth")
        assertEquals(Generator("auth", auth).run().files, Generator("auth", auth).run().files)
        val all = a.values.joinToString("")
        assertFalse(Fixtures.repoRoot.toString() in all || System.getProperty("user.name") in all, "no paths or user names in output")
        assertFalse("\r" in all)
    }

    @Test
    fun theCommittedAuthTreeIsWhatThePinnedContractGenerates() {
        val generated = Fixtures.repoRoot.resolve("bricks/auth/domain/src/commonMain/generated")
        val tests = Fixtures.repoRoot.resolve("bricks/auth/domain/src/commonTest/generated")
        val existing = java.util.TreeMap(Generator.existingFiles(generated))
        Generator.existingFiles(tests).forEach { (k, v) -> existing[Generator.TEST_PREFIX + k] = v }
        val produced = Generator("auth", Fixtures.repoRoot.resolve("contracts/auth")).run(
            existing[Generator.LOCK]?.let(::parseJsonObject),
        ).files
        assertEquals(emptyList(), Generator.diff(existing, produced))
    }

    @Test
    fun aLockedNameNeverChangesSilently() {
        val first = Generator("constructs", constructs).run()
        val lock = parseJsonObject(first.files.getValue(Generator.LOCK))
        Generator("constructs", constructs).run(lock)
        val renamed = parseJsonObject(first.files.getValue(Generator.LOCK).replace("\"listItems\"", "\"items\""))
        val e = assertThrows<GenerationFailed> { Generator("constructs", constructs).run(renamed) }
        assertTrue("E-NAME-LOCK" in e.message.orEmpty() && "items -> listItems" in e.message.orEmpty())
        Generator("constructs", constructs, acceptRenames = true).run(renamed)
        val removed = parseJsonObject(first.files.getValue(Generator.LOCK).replace("\"operations\": {", "\"operations\": {\n\"constructs_api_v1_gone\": \"gone\","))
        val gone = assertThrows<GenerationFailed> { Generator("constructs", constructs).run(removed) }
        assertTrue("left the contract" in gone.message.orEmpty())
    }

    private fun ratchet(dir: Path, ratchet: String?, previousReport: String?, update: Boolean = false): List<String> {
        ratchet?.let { Files.writeString(dir.resolve("ratchet.json"), it) } ?: Files.deleteIfExists(dir.resolve("ratchet.json"))
        val g = Generator("demo", dir).run()
        return Generator.ratchet(ContractSet.load("demo", dir), g.report, previousReport, update).first
    }

    @Test
    fun theRatchetOnlyMovesDown() {
        val undocumented = """{"T":{"type":"object","properties":{"a":{"type":"string"}},"required":["a"]}}"""
        val dir = Fixtures.contract(
            components = undocumented,
            paths = "{" + Fixtures.get("demo_api_v1_t_retrieve", "t", """{"${'$'}ref":"#/components/schemas/T"}""") + "}",
        )
        val report = Generator("demo", dir).run().report
        assertEquals(emptyList(), ratchet(dir, """{"diagnostics":{"W-DOC-PROPERTY":1,"W-DOC-TYPE":1},"overlay_entries":0}""", report))
        assertTrue(ratchet(dir, """{"diagnostics":{"W-DOC-PROPERTY":1},"overlay_entries":0}""", report).any { "W-DOC-TYPE: 1 > baseline 0" in it }, "above baseline")
        assertTrue(ratchet(dir, """{"diagnostics":{"W-DOC-PROPERTY":2,"W-DOC-TYPE":1},"overlay_entries":0}""", report).any { "lower the baseline" in it }, "below baseline must be lowered")
        assertTrue(ratchet(dir, null, report).any { "no contracts/demo/ratchet.json" in it })
        val emptyPrevious = report.replace(Regex("\"diagnostics\": \\[[\\s\\S]*?\\n    \\]"), "\"diagnostics\": []")
        assertTrue(ratchet(dir, """{"diagnostics":{"W-DOC-PROPERTY":1,"W-DOC-TYPE":1},"overlay_entries":0}""", emptyPrevious).any { "new undocumented element" in it }, "zero tolerance on additions")
        assertTrue(ratchet(dir, """{"diagnostics":{"W-DOC-PROPERTY":0,"W-DOC-TYPE":1},"overlay_entries":0}""", report, update = true).any { "never rises" in it })
    }

    @Test
    fun theOverlayBudgetIsRatcheted() {
        val dir = Fixtures.contract(
            components = """{"T":{"type":"object","description":"T.","properties":{"a":{"type":"string","description":"A."}},"required":["a"]}}""",
            paths = "{" + Fixtures.get("demo_api_v1_t_retrieve", "t", """{"${'$'}ref":"#/components/schemas/T"}""") + "}",
            overlay = """{"operations":{"demo_api_v1_t_retrieve":{"fixes":"C1","auth":"none","alias":"thing"}}}""",
        )
        val report = Generator("demo", dir).run().report
        assertEquals(emptyList(), ratchet(dir, """{"diagnostics":{},"overlay_entries":2}""", report))
        assertTrue(ratchet(dir, """{"diagnostics":{},"overlay_entries":1}""", report).any { "overlay: 2 entries > budget 1" in it })
        assertTrue(Generator("demo", dir).run().files.values.any { "public suspend fun thing()" in it }, "overlay alias")
    }

    @Test
    fun namesFollowSection4() {
        assertEquals("PatchedOrg", Names.typeName("Patched_Org"))
        assertEquals("Org", Names.typeName("_Org"))
        assertEquals("isEmailVerified", Names.member("is_email_verified"))
        assertEquals("trueLabel", Names.member("trueLabel"))
        assertEquals("object_", Names.member("object"))
        assertEquals("LoggedIn", Names.enumEntry("LOGGED_IN"))
        assertEquals("V2fa", Names.enumEntry("2fa"))
        assertEquals("GdprClosureTokenExpired", Names.errorClass("error.401.gdpr.closure_token_expired"))
        val name = { id: String, shared: Boolean, params: List<String> -> Names.operationName(id, "auth", params) { shared } }
        assertEquals("emailVerify", name("auth_api_v1_email_verify_create", false, emptyList()))
        assertEquals("createSessions", name("auth_api_v1_sessions_create", true, emptyList()))
        assertEquals("listSessions", name("auth_api_v1_sessions_list", false, emptyList()))
        assertEquals("patchPasskey", name("auth_api_v1_passkey_partial_update", false, emptyList()))
        assertEquals("deleteSessionsBySessionId", name("auth_api_v1_sessions_destroy_2", false, listOf("session_id")))
        assertEquals("getMeV2", name("auth_api_v2_me_retrieve", false, emptyList()))
        assertEquals("jwtStatus", name("jwt_status", false, emptyList()))
    }

    @Test
    fun kdocIsEscapedForBlockCommentsAndDokka() {
        assertEquals("a *&#47; b /&#42; c", KDoc.escape("a */ b /* c"))
        assertEquals("see \\[x\\] and [y](https://e.x)", KDoc.escape("see [x] and [y](https://e.x)"))
        assertEquals("`x`", KDoc.escape("``x``"))
        assertEquals("&#64;user", KDoc.escape("@user"))
        assertTrue(Fixtures.repoRoot.resolve("contracts/auth/pin.json").exists())
    }
}
