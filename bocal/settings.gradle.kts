pluginManagement {
    includeBuild("build-logic")
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
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

// Bocal — backendless music-practice companion (working name; see README §Naming).
// Every :core:* module hides its implementation behind an interface ("liftable seams")
// so it can be extracted into a standalone library later.
rootProject.name = "bocal"

include(":app")

// Pure-JVM cores — no Android dependency; tests run headless (Tier 1).
include(":core:model")
include(":core:pitch")
include(":core:timbre")
include(":core:baseline") // CLOSED (proprietary) — see core/baseline/LICENSE
include(":core:data")
include(":core:hyle")
include(":core:haptics")
include(":test-fixtures")

// Android modules.
include(":core:audio")
include(":feature:tuner")
