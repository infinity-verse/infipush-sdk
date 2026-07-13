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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven { url = uri("https://jitpack.io") }
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/infinity-verse/ads-sdk")
            credentials {
                username = "infinity-verse"
                password = "REMOVED_FOR_SECURITY"
            }
        }
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/infinity-verse/infipush-sdk")
            credentials {
                username = "infinity-verse"
                password = "REMOVED_FOR_SECURITY"
            }
        }
    }
}

rootProject.name = "infipush-sdk"



