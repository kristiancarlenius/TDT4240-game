pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "LootemShootem"
include("server", "shared", "core", "android", "desktop")
