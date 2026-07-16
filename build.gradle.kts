plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    compileOnly("com.zaxxer:HikariCP:6.3.0")
    compileOnly("redis.clients:jedis:6.0.0")
    compileOnly("org.postgresql:postgresql:42.7.7")
    compileOnly("org.slf4j:slf4j-api:2.0.16")
    compileOnly("net.kyori:adventure-api:4.17.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.17.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

group = "xyz.fireworkwars"
version = "0.2.0"
