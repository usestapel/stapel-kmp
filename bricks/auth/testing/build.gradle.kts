// stapel-auth-testing: the auth test kit for apps' own tests. The UI test tags of stapel-auth-ui (re-exported
// through the api edge) and walkers that drive the real sign-in screens on a ComposeUiTest, e.g. the mock-OTP
// sign-in a prototype-posture backend accepts. Usable from a generated app's commonTest (jvm + iOS simulator).
plugins {
    id("stapel.brick-testing")
}

stapel {
    description = "Test kit for apps using stapel-auth-ui: the screens' test tags and a mock-OTP sign-in walker for Compose UI tests."
}
