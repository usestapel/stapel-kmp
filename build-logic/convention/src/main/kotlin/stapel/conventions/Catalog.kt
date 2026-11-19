package stapel.conventions

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider

internal val Project.libs: VersionCatalog
    get() = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

internal fun Project.lib(alias: String): Provider<MinimalExternalModuleDependency> =
    libs.findLibrary(alias).orElseThrow { GradleException("Catalog has no library '$alias'") }

internal fun Project.catalogVersion(alias: String): String =
    libs.findVersion(alias).orElseThrow { GradleException("Catalog has no version '$alias'") }.requiredVersion
