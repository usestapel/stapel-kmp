package stapel.app.di.export

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.koin.dsl.koinApplication
import stapel.app.di.BrickContext
import stapel.app.di.StapelBrick
import stapel.app.navManifestJson
import stapel.result.InternalStapelApi
import java.io.File

/**
 * Build-time entry of `exportNavManifest` / `checkNavManifest` (not an app API): instantiates the brick
 * descriptor [args]`[0]` (a class with a no-argument constructor, see [StapelBrick]), builds its
 * navigation against an empty graph and writes the manifest JSON for artifact [args]`[1]` at version
 * [args]`[2]` to the file [args]`[3]`.
 */
@InternalStapelApi
public fun main(args: Array<String>) {
    require(args.size == 4) { "usage: <descriptor class> <group:artifact> <version> <out file>" }
    val (descriptor, artifact, version, out) = args
    val type = try {
        Class.forName(descriptor)
    } catch (e: ClassNotFoundException) {
        throw IllegalStateException(
            "exportNavManifest: no brick descriptor '$descriptor'. A brick UI library exposes one StapelBrick " +
                "implementation named <Brick>Brick in its di package, with a no-argument constructor.",
            e,
        )
    }
    val brick = type.getDeclaredConstructor().newInstance() as? StapelBrick
        ?: error("exportNavManifest: $descriptor does not implement stapel.app.di.StapelBrick")
    val koin = koinApplication { }.koin
    val contribution = brick.navigation(BrickContext(koin, CoroutineScope(Job())))
        ?: error("exportNavManifest: $descriptor declares no navigation; a brick UI library without screens has no manifest")
    File(out).apply { parentFile?.mkdirs() }.writeText(navManifestJson(contribution.manifest, artifact, version))
}
