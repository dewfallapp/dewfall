pluginManagement {
    // Convention plugins shared by every module. See build-logic/.
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
        // NewPipeExtractor publishes its releases only on JitPack, which builds them from
        // TeamNewPipe's Git tags. Gradle looks for TeamNewPipe's group only there, and for nothing
        // else there. The group's case must match the POMs, which name nanojson, a dependency of
        // NewPipeExtractor, as com.github.TeamNewPipe. See docs/architecture.md#build-settings.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") { name = "JitPack" } }
            filter { includeGroup("com.github.TeamNewPipe") }
        }
    }
}

rootProject.name = "dewfall"

// Every module's last name segment must be unique in the build. See
// docs/architecture.md#module-names.
include(":app")

include(":core:data")

include(":core:database")

include(":core:designsystem")

include(":core:logging")

include(":core:model")

include(":core:mvi")

include(":core:youtube")
