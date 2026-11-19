package stapel.conventions

import androidx.room3.gradle.RoomExtension
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

internal const val PUBLIC_REPO_URL = "https://github.com/usestapel/stapel-kmp"

/** `stapel.dokka`: zero undocumented public symbols (D12/R11); `dokkaGeneratePublicationHtml` is the gate. */
internal fun Project.applyStapelDokka(ext: StapelExtension) {
    pluginManager.apply("org.jetbrains.dokka")
    val names = stapelNames()
    extensions.configure(DokkaExtension::class.java) {
        moduleName.set(names.artifactId)
        dokkaPublications.configureEach { failOnWarning.set(true) }
        dokkaSourceSets.configureEach {
            reportUndocumented.set(true)
            documentedVisibilities.set(setOf(VisibilityModifier.Public, VisibilityModifier.Protected))
            skipEmptyPackages.set(true)
            suppressAnnotatedWith.addAll(INTERNAL_API_MARKERS)
            perPackageOption {
                matchingRegex.set(ext.ratchetedPackages.map { it.joinToString("|").ifEmpty { "^$" } })
                reportUndocumented.set(false)
            }
        }
    }
    tasks.named("stapelCheck").configure { dependsOn("dokkaGeneratePublicationHtml") }
}

/**
 * `stapel.publish`: coordinates, POM, a file "staging" repository under the root build dir, signing only
 * when a key is present. Nothing here publishes to a remote; Central uploads happen only in the release
 * workflow (owner bootstrap first).
 */
internal fun Project.applyStapelPublish(ext: StapelExtension) {
    pluginManager.apply("com.vanniktech.maven.publish")
    val names = stapelNames()
    val signingKeyPresent = providers.gradleProperty("signingInMemoryKey").isPresent
    extensions.configure(MavenPublishBaseExtension::class.java) {
        // version comes from project.version (set by stapel.base from VERSION_NAME)
        coordinates(mavenGroup(this@applyStapelPublish), names.artifactId)
        if (pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
            configure(KotlinMultiplatform(JavadocJar.Empty(), SourcesJar.Sources()))
        }
        publishToMavenCentral()
        if (signingKeyPresent) signAllPublications()
        pom {
            name.set(names.artifactId)
            description.set(ext.description)
            inceptionYear.set("2026")
            url.set(PUBLIC_REPO_URL)
            licenses {
                license {
                    name.set("MIT")
                    url.set("https://opensource.org/licenses/MIT")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("usestapel")
                    name.set("Stapel")
                    url.set("https://github.com/usestapel")
                }
            }
            scm {
                url.set(PUBLIC_REPO_URL)
                connection.set("scm:git:git://github.com/usestapel/stapel-kmp.git")
                developerConnection.set("scm:git:ssh://git@github.com/usestapel/stapel-kmp.git")
            }
        }
    }
    extensions.configure(PublishingExtension::class.java) {
        repositories.maven {
            name = "staging"
            url = uri(rootProject.layout.buildDirectory.dir("staging-repo"))
        }
    }
    val checkDescription = tasks.register("checkPublishedDescription") {
        group = STAPEL_GATES
        description = "A published module declares stapel { description = ... } (POM description)."
        val desc = ext.description
        val path = path
        doLast {
            if (desc.orNull.isNullOrBlank()) {
                throw org.gradle.api.GradleException("$path: published module needs stapel { description = \"...\" }")
            }
        }
    }
    tasks.named("check").configure { dependsOn(checkDescription) }
    registerCheckReleaseCoordinates()
    tasks.named("stapelCheck").configure { dependsOn("publishAllPublicationsToStagingRepository") }
}

/** `stapel.room`: KSP + Room 3 + committed schemas. Only `*-room` artifacts may apply it (D6/D9). */
internal fun Project.applyStapelRoom() {
    if (!name.endsWith("-room")) {
        throw org.gradle.api.GradleException(
            "$path applies stapel.room, but only `*-room` artifacts may depend on Room (D6/D9).",
        )
    }
    pluginManager.apply("com.google.devtools.ksp")
    pluginManager.apply("androidx.room3")
    extensions.configure(RoomExtension::class.java) {
        schemaDirectory("$projectDir/schemas")
    }
    val kmp = extensions.getByType(KotlinMultiplatformExtension::class.java)
    kmp.sourceSets.getByName("commonMain").dependencies {
        api(lib("room3-runtime"))
        implementation(lib("androidx-sqlite-bundled"))
    }
    listOf("kspAndroid", "kspJvm", "kspIosArm64", "kspIosSimulatorArm64").forEach { conf ->
        dependencies.add(conf, lib("room3-compiler"))
    }
}
