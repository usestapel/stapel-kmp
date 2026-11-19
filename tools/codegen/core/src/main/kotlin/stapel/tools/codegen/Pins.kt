package stapel.tools.codegen

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists

/**
 * `pins sync` / `pins check` (§9.1): the pinned bytes of a brick's contract, vendored into
 * `contracts/<brick>/`, identified by a commit (never an annotated tag's own object) and digests.
 */
object Pins {
    /** Default vendored file → source path in the brick repository. */
    val DEFAULT_FILES: Map<String, String> = sortedMapOf(
        ContractSet.SCHEMA to "docs/schema.json",
        ContractSet.ERRORS to "docs/errors.json",
        ContractSet.FLOWS to "docs/flows.json",
        ContractSet.CAPABILITIES to "docs/capabilities.json",
    )

    fun git(repo: Path, vararg args: String): ByteArray {
        val p = ProcessBuilder(listOf("git", "-C", repo.toString()) + args).redirectErrorStream(false).start()
        val out = p.inputStream.readAllBytes()
        val err = p.errorStream.readAllBytes()
        check(p.waitFor(60, TimeUnit.SECONDS)) { "git ${args.toList()} timed out" }
        if (p.exitValue() != 0) throw GenerationFailed("git ${args.joinToString(" ")} failed: " + String(err).trim())
        return out
    }

    private fun versionAt(repo: Path, ref: String): String {
        val toml = runCatching { String(git(repo, "show", "$ref:pyproject.toml")) }.getOrNull() ?: return "unknown"
        return Regex("(?m)^version\\s*=\\s*\"([^\"]+)\"").find(toml)?.groupValues?.get(1) ?: "unknown"
    }

    /** Vendors [files] of [tag] from [repo] into [dir] and writes `pin.json`. Returns the pin. */
    fun sync(repo: Path, repoUrl: String, module: String, tag: String, dir: Path, files: Map<String, String> = DEFAULT_FILES): Pin {
        val ref = String(git(repo, "rev-parse", "$tag^{commit}")).trim()
        val kind = String(git(repo, "cat-file", "-t", ref)).trim()
        if (kind != "commit") throw GenerationFailed("$tag resolves to a $kind, not a commit")
        Files.createDirectories(dir)
        val pinned = java.util.TreeMap<String, PinnedFile>()
        for ((name, source) in files) {
            val bytes = runCatching { git(repo, "show", "$ref:$source") }.getOrNull() ?: continue
            Files.write(dir.resolve(name), bytes)
            pinned[name] = PinnedFile(source, sha256(bytes))
        }
        if (ContractSet.SCHEMA !in pinned) throw GenerationFailed("$tag has no ${files[ContractSet.SCHEMA]}")
        val pin = Pin(module, repoUrl, tag, ref, versionAt(repo, ref), pinned)
        Files.writeString(dir.resolve(ContractSet.PIN), prettyJson(toJson(pin)))
        return pin
    }

    fun toJson(pin: Pin): JsonObject = JsonObject(
        mapOf(
            "module" to JsonPrimitive(pin.module),
            "repo" to JsonPrimitive(pin.repo),
            "tag" to JsonPrimitive(pin.tag),
            "ref" to JsonPrimitive(pin.ref),
            "version" to JsonPrimitive(pin.version),
            "files" to JsonObject(
                pin.files.mapValues { (_, f) -> JsonObject(mapOf("source" to JsonPrimitive(f.source), "sha256" to JsonPrimitive(f.sha256))) },
            ),
        ),
    )

    /**
     * Problems with the vendored copy: digests that do not match the pin and, when [repo] is given,
     * bytes that differ from the sibling at the pinned commit (or a ref that is not a commit).
     */
    fun check(dir: Path, repo: Path?): List<String> {
        val pinFile = dir.resolve(ContractSet.PIN)
        if (!pinFile.exists()) return listOf("$dir has no ${ContractSet.PIN}")
        val pin = Pin.parse(parseJson.parseToJsonElement(Files.readString(pinFile)) as JsonObject)
        val problems = mutableListOf<String>()
        if (!Regex("[0-9a-f]{40}").matches(pin.ref)) problems += "ref ${pin.ref} is not a 40-hex commit"
        for ((name, f) in pin.files) {
            val path = dir.resolve(name)
            if (!path.exists()) {
                problems += "$name pinned but missing"
                continue
            }
            val bytes = Files.readAllBytes(path)
            if (sha256(bytes) != f.sha256) problems += "$name: digest differs from pin.json"
            if (repo != null) {
                val upstream = runCatching { git(repo, "show", "${pin.ref}:${f.source}") }.getOrNull()
                if (upstream == null) problems += "$name: ${f.source} not found at ${pin.ref}"
                else if (!upstream.contentEquals(bytes)) problems += "$name: differs from ${f.source} at ${pin.ref}"
            }
        }
        if (repo != null) {
            val kind = runCatching { String(git(repo, "cat-file", "-t", pin.ref)).trim() }.getOrDefault("missing")
            if (kind != "commit") problems += "ref ${pin.ref} is a $kind in $repo, not a commit"
            val tagRef = runCatching { String(git(repo, "rev-parse", "${pin.tag}^{commit}")).trim() }.getOrNull()
            if (tagRef != null && tagRef != pin.ref) problems += "tag ${pin.tag} is $tagRef, pin says ${pin.ref}"
        }
        return problems
    }
}
