plugins {
    id("fabric-loom")
    id("com.modrinth.minotaur")
}

version = "${sc.properties.get<String>("mod.version")}+${sc.current.version}"
base.archivesName = sc.properties.get<String>("mod.id")

val mcVersion: String = sc.current.version

// Game versions this branch is published for - the same list the mod metadata uses.
val publishedVersions: List<String> = sc.properties.rawOrNull("mod", "mc_releases")
    ?.asList().orEmpty().map { it.toString() }

// The rendering code differs enough between 1.21.4 and 1.21.9+ that it lives in
// per-version directories instead of giant conditional blocks.
sourceSets["main"].java.srcDir(rootProject.file("src/$mcVersion/java"))

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings("net.fabricmc:yarn:${sc.properties.get<String>("deps.yarn")}:v2")

    modImplementation("net.fabricmc:fabric-loader:${sc.properties.get<String>("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")

    // Development only: gives the dev client a mod list screen, which is the only
    // place the mod icon and metadata can actually be looked at in game. Never
    // ends up in the published jar.
    sc.properties.rawOrNull("deps", "mod_menu")?.let {
        modLocalRuntime("maven.modrinth:modmenu:$it")
    }
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

// ─────────────────────────────────────────────────────────────────────────────
//  Publishing to Modrinth
//
//  MODRINTH_TOKEN=... ./gradlew publishAllToModrinth   (every branch)
//  MODRINTH_TOKEN=... ./gradlew :1.21.11:modrinth      (one branch)
//
//  The token is read lazily, so an ordinary build never needs it. The project has
//  to exist on Modrinth first - Minotaur uploads versions, it does not create
//  projects.
// ─────────────────────────────────────────────────────────────────────────────
modrinth {
    token = providers.environmentVariable("MODRINTH_TOKEN")
    projectId = providers.gradleProperty("abl.modrinthId").orElse(sc.properties.get<String>("mod.id"))
    versionNumber = project.version.toString()
    versionName = "${sc.properties.get<String>("mod.name")} ${sc.properties.get<String>("mod.version")} for $mcVersion"
    versionType = "release"
    // ./gradlew :1.21.11:modrinth -Pabl.modrinthDry  -> prints what would be sent,
    // uploads nothing. Handy for checking the metadata before the real run.
    debugMode = providers.gradleProperty("abl.modrinthDry").map { true }.orElse(false)
    // With Loom this has to be remapJar, not jar - jar still holds named mappings.
    uploadFile.set(tasks.remapJar)
    gameVersions.addAll(publishedVersions)
    loaders.add("fabric")
    changelog = providers.provider {
        val file = rootProject.file("CHANGELOG.md")
        if (file.exists()) file.readText() else "See the repository for the list of changes."
    }
    // The project page gets its own text: README is aimed at whoever builds the
    // mod, MODRINTH.md at whoever installs it.
    syncBodyFrom = providers.provider {
        val page = rootProject.file("MODRINTH.md")
        (if (page.exists()) page else rootProject.file("README.md")).readText()
    }
    dependencies {
        required.project("fabric-api")
    }
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
