plugins {
    id("dev.kikugie.stonecutter")
}

// The active branch - what the IDE and `./gradlew :<version>:build` work against.
// Switch with: ./gradlew "Set active project to 1.21.4"
stonecutter active "1.21.11"

// One jar per supported game version, collected in build/libs/<mod version>/.
tasks.register("buildAll") {
    group = "build"
    description = "Builds the jars for every supported Minecraft version"
    dependsOn(subprojects.map { it.tasks.named("buildAndCollect") })
}
