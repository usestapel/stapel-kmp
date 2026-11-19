// stapel-input: the pure input engine — total transformations raw value <-> displayed text with
// cursor mapping, paste normalisation, masks (digits, pattern, phone, date, decimal/money, codes),
// a validator algebra whose messages are i18n keys, and rule sets built from data. No Compose:
// presenters and tests use it directly; stapel-input-compose binds it to text fields.
plugins {
    id("stapel.substrate")
}

stapel {
    description = "Input masks and validation for Stapel clients: total formatting with cursor mapping, phone, date, money, codes, data-driven rules."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelWire)
            api(projects.stapelI18n)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.serialization.json)
        }
    }
}
