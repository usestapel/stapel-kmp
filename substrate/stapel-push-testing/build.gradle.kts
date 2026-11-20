// stapel-push-testing: fakes of the push seams and the contract suite every push provider passes.
// Published so provider artifacts and apps depend on it from test source sets; never a main dependency.
plugins {
    id("stapel.substrate")
}

stapel {
    description = "Test kit for stapel-push: a scriptable fake provider, an in-memory device registry with the backend's rules, and the provider contract suite."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelPush)
            api(libs.kotlinx.coroutines.core)
        }
    }
}
