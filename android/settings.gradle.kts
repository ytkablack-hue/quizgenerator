pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content { includeModule("cz.adaptech.tesseract4android", "tesseract4android") }
        }
    }
}

rootProject.name = "QuizGenerator"
include(":app", ":ui")
