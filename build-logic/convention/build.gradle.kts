plugins {
    `kotlin-dsl`
}

group = "io.github.dewfallapp.buildlogic"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    // The root build.gradle.kts puts these plugins on the build classpath.
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "dewfall.android.application"
            implementationClass =
                "io.github.dewfallapp.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "dewfall.android.library"
            implementationClass = "io.github.dewfallapp.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "dewfall.android.compose"
            implementationClass = "io.github.dewfallapp.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "dewfall.android.hilt"
            implementationClass = "io.github.dewfallapp.buildlogic.AndroidHiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "dewfall.jvm.library"
            implementationClass = "io.github.dewfallapp.buildlogic.JvmLibraryConventionPlugin"
        }
        register("detekt") {
            id = "dewfall.detekt"
            implementationClass = "io.github.dewfallapp.buildlogic.DetektConventionPlugin"
        }
    }
}
