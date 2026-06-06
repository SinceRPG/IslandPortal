plugins {
    id("java-library")
    alias(libs.plugins.paperweight.userdev)
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

repositories {
    mavenCentral()
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.codemc.org/repository/maven-public/")
    maven("https://repo.bg-software.com/repository/api/")
    maven("https://repo.euphyllia.moe/repository/maven-public/")
    maven("https://maven.devs.beer/")
    maven("https://repo.oraxen.com/releases")
    maven("https://repo.nexomc.com/releases")
    maven("https://mvn.lumine.io/repository/maven-public/")
    maven("https://nexus.phoenixdevt.fr/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.essentialsx.net/releases/")
    maven("https://repo.essentialsx.net/snapshots/")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":minion"))
    paperweight.paperDevBundle(libs.versions.paper.api.get())
    compileOnly("world.bentobox:bentobox:2.7.0-SNAPSHOT")
    compileOnly("com.bgsoftware:SuperiorSkyblockAPI:2026.1")
    compileOnly("fr.euphyllia.skyllia:api:3.+")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("com.github.brcdev-minecraft:shopgui-api:3.2.0") {
        exclude(group = "org.spigotmc", module = "spigot-api")
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("net.essentialsx:EssentialsX:2.21.2") {
        isTransitive = false
    }
    compileOnly("com.github.Zrips:CMI-API:9.8.6.4") {
        isTransitive = false
    }
    compileOnly("com.github.retrooper:packetevents-spigot:2.11.2")
    compileOnly("com.arcaniax:HeadDatabase-API:1.3.2")
    compileOnly("dev.lone:api-itemsadder:4.0.10")
    compileOnly("io.th0rgal:oraxen:1.213.0") {
        isTransitive = false
    }
    compileOnly("com.nexomc:nexo:1.23") {
        isTransitive = false
    }
    compileOnly("io.lumine:MythicLib-dist:1.7.1-SNAPSHOT")
    compileOnly("net.Indyuce:MMOItems-API:6.10.1-SNAPSHOT")
    compileOnly("io.lumine:Mythic-Dist:5.12.1") {
        isTransitive = false
    }
    compileOnly("com.sk89q.worldedit:worldedit-core:7.3.16") {
        isTransitive = false
    }
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.16") {
        isTransitive = false
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Werror"))
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion(libs.versions.minecraft.get())
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }
}
