plugins {
    id("java-library")
    alias(libs.plugins.paperweight.userdev)
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

repositories {
    mavenCentral()
    maven("https://repo.codemc.org/repository/maven-public/")
    maven("https://repo.bg-software.com/repository/api/")
    maven("https://repo.euphyllia.moe/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle(libs.versions.paper.api.get())
    compileOnly("world.bentobox:bentobox:2.7.0-SNAPSHOT")
    compileOnly("com.bgsoftware:SuperiorSkyblockAPI:2026.1")
    compileOnly("fr.euphyllia.skyllia:api:3.+")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
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
