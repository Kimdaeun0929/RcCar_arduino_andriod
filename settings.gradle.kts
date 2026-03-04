pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // 라이브러리를 다시 쓰려면 이 줄이 필요하지만,
        // 지금은 빌드 성공을 위해 일단 확인만 하세요.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "rccar"
include(":app")