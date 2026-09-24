// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.android") version "2.3.0" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
    id("com.google.devtools.ksp") version "2.3.0" apply false
}

allprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        autoCorrect = false
    }
}
