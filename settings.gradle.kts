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
        // JitPack is required for the PhotoView library
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "MyASTApp"
include(":app")