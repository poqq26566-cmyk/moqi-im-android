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
        // JitPack for Sherpa-onnx: maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "moqi-im-android"
include(":app")