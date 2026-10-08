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
        // O NewPipeExtractor é publicado no JitPack (compila diretamente do GitHub)
        maven("https://jitpack.io")
    }
}

rootProject.name = "MeuTube"
include(":app")
