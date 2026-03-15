plugins {
    kotlin("jvm") version "2.3.0"
    id("fabric-loom") version "1.11.7"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:1.21.10+build.1:mergedv2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("fabric_kotlin_version")}")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
}

tasks.processResources {
    inputs.property("version", project.version)
    filteringCharset = "UTF-8"
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}
