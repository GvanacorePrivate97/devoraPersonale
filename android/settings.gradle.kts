pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "de-vito"

include(":app")
include(":core:designsystem")
include(":core:model")
include(":core:common")
include(":core:data")
include(":core:network")
include(":core:ui")
include(":feature:auth")
include(":feature:client")
include(":feature:staff")
include(":feature:admin")
