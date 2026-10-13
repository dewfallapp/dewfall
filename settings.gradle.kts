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
    }
}

rootProject.name = "dewfall"

// Every module's last name segment must be unique in the build. See
// docs/architecture.md#module-names.
include(":app")

include(":core:data")

include(":core:database")

include(":core:designsystem")

include(":core:model")

include(":core:mvi")

include(":core:youtube")
