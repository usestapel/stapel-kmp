plugins {
    id("stapel.substrate")
    id("stapel.room")
}

stapel {
    description = "Durable storage for stapel-cache on Room 3: one cache.db per partition, physical wipe."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelCache)
            implementation(projects.stapelDi)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.io.core)
        }
        getByName("sqliteTest").dependencies {
            implementation(projects.stapelCacheTesting)
        }
    }
}
