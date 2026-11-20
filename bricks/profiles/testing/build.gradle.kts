// stapel-profiles-testing: the profiles test kit for apps' own tests. The UI test tags of stapel-profiles-ui
// (re-exported through the api edge), walkers that drive the real settings screens on a ComposeUiTest, and
// sample domain values for previews. Usable from a generated app's commonTest (jvm + iOS simulator).
plugins {
    id("stapel.brick-testing")
}

stapel {
    description = "Test kit for apps using stapel-profiles-ui: the screens' test tags, settings walkers for Compose UI tests and sample profiles."
}
