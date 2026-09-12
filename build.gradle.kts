plugins {
    id("fabric-loom")
}

version = "${sc.properties.get<String>("mod.version")}+${sc.current.version}"
base.archivesName = sc.properties.get<String>("mod.id")

val mcVersion: String = sc.current.version

// The rendering code differs enough between 1.21.4 and 1.21.9+ that it lives in
// per-version directories instead of giant conditional blocks.
sourceSets["main"].java.srcDir(rootProject.file("src/$mcVersion/java"))

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings("net.fabricmc:yarn:${sc.properties.get<String>("deps.yarn")}:v2")

    modImplementation("net.fabricmc:fabric-loader:${sc.properties.get<String>("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")
}

loom {
    // ./gradlew runClient -Pabl.quickPlay="New World" jumps straight into a world
    val quickPlay: String? = providers.gradleProperty("abl.quickPlay").orNull
    if (quickPlay != null) {
        runConfigs.named("client") { programArgs("--quickPlaySingleplayer", quickPlay) }
    }

    runConfigs.all {
        preferGradleTask = true
        runDirectory = rootProject.file("run/$mcVersion")
        property("mixin.debug.export", "true")
        if (providers.gradleProperty("abl.blurtest").isPresent) property("antibaseleak.blurtest", "true")
        property("antibaseleak.selftest", "true")
    }
    runConfigs.named("server") { ideConfigGenerated(false) }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 21
    }

    processResources {
        val props = mapOf(
            "id"        to sc.properties.get<String>("mod.id"),
            "name"      to sc.properties.get<String>("mod.name"),
            "version"   to project.version.toString(),
            "minecraft" to sc.properties.get<String>("mod.mc_compat"),
            "loader"    to sc.properties.get<String>("mod.loader_min")
        )
        props.forEach { (k, v) -> inputs.property(k, v) }
        filesMatching("fabric.mod.json") { expand(props) }
    }

    // Collects the jars of every branch into build/libs/<mod.version>/
    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the jar and copies it into build/libs/<mod version>/"
        from(remapJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/${sc.properties.get<String>("mod.version")}"))
        dependsOn(build)
    }
}
