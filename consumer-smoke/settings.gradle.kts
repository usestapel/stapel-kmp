// A SEPARATE Gradle build that consumes stapel-kmp the way a client does: by Maven coordinates,
// through Gradle module metadata, never through project dependencies.
//
//   ./gradlew -p consumer-smoke check                       # repo = ../build/staging-repo (CI gate)
//   ./gradlew -p consumer-smoke check -Pstapel.repo=local   # repo = ~/.m2 (after publishToMavenLocal)
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.android(\\..*)?")
                includeGroupByRegex("com\\.google\\..*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

val stapelProps = java.util.Properties().apply { file("../gradle.properties").reader().use { load(it) } }
val stapelGroup: String = stapelProps.getProperty("stapel.group")

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        val repo = providers.gradleProperty("stapel.repo").getOrElse("staging")
        if (repo == "local") {
            mavenLocal { content { includeGroup(stapelGroup) } }
        } else {
            maven {
                name = "stapelStaging"
                url = uri(file("../build/staging-repo"))
                content { includeGroup(stapelGroup) }
            }
        }
        google {
            content {
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.android(\\..*)?")
                includeGroupByRegex("com\\.google\\..*")
            }
        }
        mavenCentral { content { excludeGroup(stapelGroup) } }
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "stapel-kmp-consumer-smoke"
