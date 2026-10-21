plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    compileOnly("com.zaxxer:HikariCP:7.1.0")
    compileOnly("redis.clients:jedis:8.0.2")
    compileOnly("org.postgresql:postgresql:42.7.14")
    compileOnly("org.slf4j:slf4j-api:2.0.20")
    compileOnly("net.kyori:adventure-api:5.2.0")
    compileOnly("net.kyori:adventure-text-minimessage:5.2.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

group = "xyz.fireworkwars"
version = "0.3.0"
