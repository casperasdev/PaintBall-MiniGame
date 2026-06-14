plugins {
    java
    id("com.gradleup.shadow") version "8.3.0"
}

group = "com.bananamc.paintball"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    implementation("com.zaxxer:HikariCP:5.1.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    processResources {
        filesMatching(listOf("plugin.yml", "paper-plugin.yml")) {
            expand("version" to project.version)
        }
    }
    shadowJar {
        archiveClassifier.set("")
        relocate("com.zaxxer.hikari", "com.bananamc.paintball.libs.hikari")
    }
    build {
        dependsOn(shadowJar)
    }
}
