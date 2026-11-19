// shop-mobile: the reference app, built the way the stapel-tools KMP emitter builds a generated app.
// A SEPARATE Gradle build: stapel artifacts, the version catalog and the two Gradle plugins come by Maven
// coordinates from the staging repository (default, ../../build/staging-repo after
// `./gradlew publishAllPublicationsToStagingRepository` at the repository root) or mavenLocal
// (-Pstapel.repo=local). Nothing here reads stapel-kmp's build-logic.
val stapelGroup = "io.github.usestapel"
val stapelVersion: String = providers.gradleProperty("stapel.version").get()
val stapelRepo: String = providers.gradleProperty("stapel.repo").getOrElse("staging")

pluginManagement {
    val group = "io.github.usestapel"
    repositories {
        if (providers.gradleProperty("stapel.repo").getOrElse("staging") == "local") {
            mavenLocal { content { includeGroupAndSubgroups(group) } }
        } else {
            maven {
                name = "stapelStaging"
                url = uri(file("../../build/staging-repo"))
                content { includeGroupAndSubgroups(group) }
            }
        }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        if (stapelRepo == "local") {
            mavenLocal { content { includeGroupAndSubgroups(stapelGroup) } }
        } else {
            maven {
                name = "stapelStaging"
                url = uri(file("../../build/staging-repo"))
                content { includeGroupAndSubgroups(stapelGroup) }
            }
        }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral { content { excludeGroupAndSubgroups(stapelGroup) } }
    }
    versionCatalogs {
        create("libs") { from("$stapelGroup:stapel-catalog:$stapelVersion") }
    }
}

rootProject.name = "shop-mobile"

include(":androidApp")
include(":desktopApp")
include(":shared")
