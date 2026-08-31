plugins {
    java
}

group = "dev.tickflow"
version = "0.1.1-SNAPSHOT"
description = "Real-time tick compensation for Bukkit-family Minecraft servers"

repositories {
    maven("https://repo.purpurmc.org/snapshots")
}

dependencies {
    compileOnly("org.purpurmc.purpur:purpur-api:26.2.build.2628-stable")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.named<org.gradle.jvm.tasks.Jar>("jar") {
    archiveFileName.set("TickFlow-${project.version}.jar")
    manifest {
        attributes(
            "Implementation-Title" to "TickFlow",
            "Implementation-Version" to project.version.toString(),
            "TickFlow-Primary-Target" to "Purpur 26.2"
        )
    }
}

