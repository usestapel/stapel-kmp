// The Kotlin half of the iOS bridge compile check: a SEPARATE Gradle build that builds the framework
// `Shared` exporting the provider artifacts' bridge protocols, from coordinates (the staging repository
// by default, mavenLocal with -Pstapel.repo=local), exactly as a scaffolded app's `shared` module does.
val stapelGroup = "io.github.usestapel"
val stapelVersion: String = providers.gradleProperty("stapel.version").get()
val stapelRepo: String = providers.gradleProperty("stapel.repo").getOrElse("staging")

pluginManagement {
    repositories {
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
        mavenCentral { content { excludeGroupAndSubgroups(stapelGroup) } }
    }
    versionCatalogs {
        create("libs") { from("$stapelGroup:stapel-catalog:$stapelVersion") }
    }
}

rootProject.name = "ios-bridges-check"
include(":shared")
