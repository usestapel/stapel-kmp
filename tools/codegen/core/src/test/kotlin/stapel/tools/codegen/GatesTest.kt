package stapel.tools.codegen

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** One fixture per diagnostic code that must trip it (§8.3 gate inversions), plus its clean twin. */
class GatesTest {
    private fun codes(dir: java.nio.file.Path): Map<String, Int> = Fixtures.model("demo", dir).diagnostics.counts()

    private fun failsWith(code: String, dir: java.nio.file.Path) {
        val e = assertThrows<GenerationFailed> { Fixtures.generate("demo", dir) }
        assertTrue(code in e.message.orEmpty(), "expected $code in:\n${e.message}")
    }

    private val obj = """{"type":"object","description":"A thing.","properties":{"id":{"type":"string","description":"Id."}},"required":["id"]}"""

    @Test
    fun cleanContractHasNoDiagnostics() {
        val dir = Fixtures.contract(components = """{"Thing":$obj}""", paths = "{" + Fixtures.get("demo_api_v1_thing_retrieve", "thing", """{"${'$'}ref":"#/components/schemas/Thing"}""") + "}")
        assertEquals(emptyMap(), codes(dir))
        Fixtures.generate("demo", dir)
    }

    @Test
    fun ambiguousUnionIsAnError() {
        val a = """{"type":"object","properties":{"x":{"type":"string"}},"required":["x"]}"""
        val dir = Fixtures.contract(
            components = """{"A":$a,"B":$a,"U":{"oneOf":[{"${'$'}ref":"#/components/schemas/A"},{"${'$'}ref":"#/components/schemas/B"}]}}""",
            paths = "{" + Fixtures.get("demo_api_v1_u_retrieve", "u", """{"${'$'}ref":"#/components/schemas/U"}""") + "}",
        )
        failsWith("E-UNION-AMBIGUOUS", dir)
    }

    @Test
    fun moneyAsDoubleIsAnError() {
        val dir = Fixtures.contract(
            components = """{"P":{"type":"object","description":"P.","properties":{"price":{"type":"number","description":"Price."}},"required":["price"]}}""",
            paths = "{" + Fixtures.get("demo_api_v1_p_retrieve", "p", """{"${'$'}ref":"#/components/schemas/P"}""") + "}",
        )
        failsWith("E-MONEY-AS-DOUBLE", dir)
    }

    @Test
    fun foldedComponentNamesCollide() {
        val dir = Fixtures.contract(components = """{"_Org":$obj,"Org":$obj}""", paths = "{" + Fixtures.get("demo_api_v1_o_retrieve", "o", """{"${'$'}ref":"#/components/schemas/Org"}""") + "}")
        failsWith("E-NAME-COLLISION", dir)
    }

    @Test
    fun operationNamesCollide() {
        val ref = """{"${'$'}ref":"#/components/schemas/Thing"}"""
        val dir = Fixtures.contract(
            components = """{"Thing":$obj}""",
            paths = "{" + Fixtures.get("demo_api_v1_a_retrieve", "a", ref) + "," + Fixtures.get("demo_api_v2_a_retrieve_x", "b", ref).replace("demo_api_v2_a_retrieve_x", "getA") + "}",
        )
        failsWith("E-NAME-COLLISION", dir)
    }

    @Test
    fun boundCodeOutsideErrorsJsonIsAnError() {
        val ref = """{"${'$'}ref":"#/components/schemas/Thing"}"""
        val responses = ""","400":{"description":"","x-stapel-errors":["error.400.nope"]}"""
        val dir = Fixtures.contract(
            components = """{"Thing":$obj}""",
            paths = "{" + Fixtures.post("demo_api_v1_t_create", "t", ref, ref, responses) + "}",
            errors = """[{"code":"error.400.real","status":400,"params":[],"remediation":"fix_input","en":"Real."}]""",
            profile = 2,
        )
        failsWith("E-ERR-UNKNOWN-CODE", dir)
    }

    @Test
    fun unsupportedTypeIsAnError() {
        val dir = Fixtures.contract(
            components = """{"T":{"type":"object","description":"T.","properties":{"x":{"type":"tuple","description":"X."}},"required":["x"]}}""",
            paths = "{" + Fixtures.get("demo_api_v1_t_retrieve", "t", """{"${'$'}ref":"#/components/schemas/T"}""") + "}",
        )
        failsWith("E-UNSUPPORTED-KEYWORD", dir)
    }

    @Test
    fun overlayForAMissingOperationIsStale() {
        val dir = Fixtures.contract(
            components = """{"Thing":$obj}""",
            paths = "{" + Fixtures.get("demo_api_v1_thing_retrieve", "thing", """{"${'$'}ref":"#/components/schemas/Thing"}""") + "}",
            overlay = """{"operations":{"demo_api_v1_gone_create":{"fixes":"C2","exclude":true}}}""",
        )
        failsWith("E-OVERLAY-STALE", dir)
    }

    @Test
    fun overlayRepeatingWhatTheContractSaysIsRedundant() {
        val ref = """{"${'$'}ref":"#/components/schemas/Thing"}"""
        val errors = """[{"code":"error.400.real","status":400,"params":[],"remediation":"fix_input","en":"Real."}]"""
        val responses = ""","400":{"description":"","x-stapel-errors":["error.400.real"]}"""
        val overlay = """{"operations":{"demo_api_v1_t_create":{"fixes":"C2","errors":{"400":["error.400.real"]}}}}"""
        failsWith("E-OVERLAY-REDUNDANT", Fixtures.contract(components = """{"Thing":$obj}""", paths = "{" + Fixtures.post("demo_api_v1_t_create", "t", ref, ref, responses) + "}", errors = errors, overlay = overlay, profile = 2))
        val auth = """{"operations":{"demo_api_v1_t_create":{"fixes":"C1","auth":"optional"}}}"""
        failsWith(
            "E-OVERLAY-REDUNDANT",
            Fixtures.contract(components = """{"Thing":$obj}""", paths = "{" + Fixtures.post("demo_api_v1_t_create", "t", ref, ref, security = """[{"JWTCookieAuth":[]},{}]""") + "}", overlay = auth, profile = 2),
        )
        // The same overlay against a profile-1 contract that lacks the binding is legitimate.
        val p1 = Fixtures.contract(components = """{"Thing":$obj}""", paths = "{" + Fixtures.post("demo_api_v1_t_create", "t", ref, ref, ""","400":{"description":""}""") + "}", errors = errors, overlay = overlay)
        Fixtures.generate("demo", p1)
    }

    @Test
    fun cachePlanIsValidated() {
        val ref = """{"${'$'}ref":"#/components/schemas/Thing"}"""
        val paths = "{" + Fixtures.get("demo_api_v1_thing_retrieve", "thing", ref) + "," + Fixtures.post("demo_api_v1_thing_create", "thing2", ref, ref) + "}"
        val bad = listOf(
            """{"operations":{"demo_api_v1_nope":{"key":["x"]}}}""",
            """{"operations":{"demo_api_v1_thing_create":{"key":["x"]}}}""",
            """{"operations":{"demo_api_v1_thing_create":{"offline":"yes"}}}""",
            """{"operations":{"demo_api_v1_thing_create":{"invalidates":[["other"]]}}}""",
            """{"operations":{"demo_api_v1_thing_retrieve":{"policy":"forever"}}}""",
        )
        bad.forEach { failsWith("E-CACHE-PLAN", Fixtures.contract(components = """{"Thing":$obj}""", paths = paths, cache = it)) }
        Fixtures.generate("demo", Fixtures.contract(components = """{"Thing":$obj}""", paths = paths, cache = """{"operations":{"demo_api_v1_thing_create":{"invalidates":[["thing"]],"offline":"natural"}}}"""))
    }

    @Test
    fun vendoredBytesMustMatchThePinDigest() {
        val dir = Fixtures.contract(components = """{"Thing":$obj}""", paths = "{" + Fixtures.get("demo_api_v1_thing_retrieve", "thing", """{"${'$'}ref":"#/components/schemas/Thing"}""") + "}")
        val digest = sha256(Files.readAllBytes(dir.resolve("schema.json")))
        val pin = """{"module":"stapel-demo","repo":"r","tag":"v1","ref":"${"a".repeat(40)}","version":"1","files":{"schema.json":{"source":"docs/schema.json","sha256":"%s"}}}"""
        Files.writeString(dir.resolve("pin.json"), pin.format(digest))
        Fixtures.generate("demo", dir)
        Files.writeString(dir.resolve("pin.json"), pin.format("0".repeat(64)))
        val e = assertThrows<GenerationFailed> { Fixtures.generate("demo", dir) }
        assertTrue("E-PIN-DIGEST" in e.message.orEmpty())
    }

    @Test
    fun warningsAreCountedPerCode() {
        val enumA = """{"type":"string","enum":["a"]}"""
        val va = """{"type":"object","properties":{"kind":{"${'$'}ref":"#/components/schemas/KA"},"x":{"type":"integer"}},"required":["kind"]}"""
        val vb = """{"type":"object","properties":{"kind":{"${'$'}ref":"#/components/schemas/KB"}},"required":["kind"]}"""
        val comps = """{"KA":$enumA,"KB":{"type":"string","enum":["b"]},"VA":$va,"VB":$vb,
            "U":{"oneOf":[{"${'$'}ref":"#/components/schemas/VA"},{"${'$'}ref":"#/components/schemas/VB"}],"discriminator":{"propertyName":"kind","mapping":{"VA":"#/components/schemas/VA"}}},
            "H":{"type":"object","properties":{"h":{"oneOf":[{"type":"array","items":{}},{"type":"boolean"}]},"n":{"type":"string","not":{"enum":["x"]}}}},
            "Page":{"type":"object","properties":{"items":{"type":"array","items":{"type":"integer"}},"has_next":{"type":"boolean"}},"required":["items","has_next"]}}"""
        val ref = { n: String -> """{"${'$'}ref":"#/components/schemas/$n"}""" }
        val paths = "{" + listOf(
            Fixtures.get("demo_api_v1_u_retrieve", "u", ref("U")),
            Fixtures.get("demo_api_v1_h_retrieve", "h", ref("H"), security = "null").replace(""""security":null,""", ""),
            Fixtures.get("demo_api_v1_page_retrieve", "page", ref("Page"), params = """[{"in":"query","name":"q","schema":{"type":"string"}}]"""),
            """"/demo/api/v1/empty/":{"post":{"operationId":"demo_api_v1_empty_create","responses":{"200":{"description":""},"400":{"description":"","content":{"application/json":{"schema":{"${'$'}ref":"#/components/schemas/StapelError"}}}}}}}""",
        ).joinToString(",") + "}"
        val c = codes(Fixtures.contract(components = comps.replace("\"Page\"", "\"StapelError\":{\"type\":\"object\",\"description\":\"E.\",\"properties\":{\"localizable_error\":{\"type\":\"string\",\"description\":\"C.\"}},\"required\":[\"localizable_error\"]},\"Page\""), paths = paths, errors = "[]", profile = 2))
        listOf(
            "W-DISC-MAPPING", "W-UNTYPED-UNION", "W-NOT-IGNORED", "W-SECURITY-ABSENT", "W-BODY-UNDECLARED", "W-PAGINATION-UNRECOGNIZED",
            "W-DOC-OPERATION", "W-DOC-PARAM", "W-DOC-TYPE", "W-DOC-PROPERTY",
        ).forEach { assertTrue((c[it] ?: 0) > 0, "$it missing from $c") }
    }

    @Test
    fun errStatusUnboundIsCountedWhenSomeStatusesHaveCodes() {
        val ref = """{"${'$'}ref":"#/components/schemas/Thing"}"""
        val se = """{"${'$'}ref":"#/components/schemas/StapelError"}"""
        val responses = ""","400":{"description":"","x-stapel-errors":["error.400.real"],"content":{"application/json":{"schema":$se}}},"409":{"description":"","content":{"application/json":{"schema":$se}}}"""
        val c = codes(
            Fixtures.contract(
                components = """{"Thing":$obj,"StapelError":$obj}""",
                paths = "{" + Fixtures.post("demo_api_v1_t_create", "t", ref, ref, responses) + "}",
                errors = """[{"code":"error.400.real","status":400,"params":[],"remediation":"fix_input","en":"Real."}]""",
                profile = 2,
            ),
        )
        assertEquals(1, c["W-ERR-STATUS-UNBOUND"])
    }
}
