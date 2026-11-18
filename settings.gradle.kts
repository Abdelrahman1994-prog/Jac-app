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

rootProject.name = "JacDrive"

include(
    ":app",
    ":core",
    ":core-ui",
    ":obd",
    ":vehicle",
    ":diagnostics",
    ":dashboard",
    ":maintenance",
    ":analytics",
    ":settings",
)
