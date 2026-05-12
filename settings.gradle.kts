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
        @Suppress("ktlint:standard:property-naming")
        val WholphinExtensionsUsername: String? by settings
        if (!WholphinExtensionsUsername.isNullOrBlank()) {
            maven("https://maven.pkg.github.com/damontecres/wholphin-extensions") {
                name = "WholphinExtensions"
                credentials(PasswordCredentials::class)
            }
        }
    }
}

rootProject.name = "Wholphin"
include(":app")
