// The gallery as a desktop app (unpublished): ./gradlew :showcase:desktop:run
plugins {
    id("stapel.showcase")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            implementation(projects.showcase.gallery)
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.desktop {
    application {
        mainClass = "stapel.showcase.desktop.MainKt"
    }
}
