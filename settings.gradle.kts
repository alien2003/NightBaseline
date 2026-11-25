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
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
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

rootProject.name = "NightBaseline"

include(":core-algo")
include(":algo-cli")
include(":data-local")
include(":data-hc")
include(":work")

// Debug-only dataset exporter (never shipped): reads Health Connect, writes JSONL into its private files dir.
include(":hc-exporter")
project(":hc-exporter").projectDir = file("tools/hc-exporter")
