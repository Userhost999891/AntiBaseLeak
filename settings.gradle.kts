pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
    // Loom is declared centrally, so the per-version script uses `id("fabric-loom")` without a version.
    plugins {
        id("fabric-loom") version "1.17.20"
        id("com.modrinth.minotaur") version "2.9.0"
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.7"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // 1.21.4  -> covers 1.21.2 - 1.21.4
        // 1.21.11 -> covers 1.21.9 - 1.21.11
        versions("1.21.4", "1.21.11")
        vcsVersion = "1.21.11"
    }
}

rootProject.name = "AntiBaseLeak"
