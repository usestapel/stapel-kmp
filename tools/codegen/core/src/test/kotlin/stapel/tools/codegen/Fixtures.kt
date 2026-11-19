package stapel.tools.codegen

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Mini-contracts written to a temp dir, one per test. */
object Fixtures {
    val repoRoot: Path = Paths.get(System.getProperty("stapel.repoRoot") ?: "../../..").toAbsolutePath().normalize()

    fun contract(
        components: String = "{}",
        paths: String = "{}",
        errors: String? = null,
        overlay: String? = null,
        cache: String? = null,
        profile: Int? = null,
        extra: Map<String, String> = emptyMap(),
    ): Path {
        val dir = Files.createTempDirectory("codegen-fixture")
        val info = if (profile != null) """{"title":"","version":"0","x-stapel-contract":$profile}""" else """{"title":"","version":"0"}"""
        Files.writeString(
            dir.resolve("schema.json"),
            """{"openapi":"3.0.3","info":$info,"paths":$paths,"components":{"schemas":$components}}""",
        )
        errors?.let { Files.writeString(dir.resolve("errors.json"), it) }
        overlay?.let { Files.writeString(dir.resolve("overlay.json"), it) }
        cache?.let { Files.writeString(dir.resolve("cache.json"), it) }
        extra.forEach { (k, v) -> Files.writeString(dir.resolve(k), v) }
        return dir
    }

    /** One GET operation returning [schemaRef] (a JSON schema) under `/demo/api/v1/<path>/`. */
    fun get(id: String, path: String, schema: String, security: String = """[{"JWTCookieAuth":[]}]""", params: String = "[]"): String =
        """"/demo/api/v1/$path/":{"get":{"operationId":"$id","description":"Doc of $id.","parameters":$params,"security":$security,
           "responses":{"200":{"description":"","content":{"application/json":{"schema":$schema}}}}}}"""

    fun post(id: String, path: String, body: String, response: String, responses: String = "", security: String = """[{"JWTCookieAuth":[]}]"""): String =
        """"/demo/api/v1/$path/":{"post":{"operationId":"$id","description":"Doc of $id.","security":$security,
           "requestBody":{"content":{"application/json":{"schema":$body}},"required":true},
           "responses":{"200":{"description":"","content":{"application/json":{"schema":$response}}}$responses}}}"""

    fun model(brick: String, dir: Path) = ContractParser(ContractSet.load(brick, dir)).parse()

    fun generate(brick: String, dir: Path, lock: kotlinx.serialization.json.JsonObject? = null) = Generator(brick, dir).run(lock)
}
