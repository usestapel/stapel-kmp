plugins {
    id("stapel.capability")
    id("stapel.serialization")
}

stapel {
    description = "Headless captcha for Stapel clients: a solver seam and the challenge, token, retry network plug-in."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelNetwork)
            implementation(projects.stapelDi)
        }
        commonTest.dependencies {
            implementation(projects.stapelConfig)
        }
    }
}
