pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") {
            content { includeGroupByRegex("net\\.fabricmc.*") }
        }
        maven("https://repo.papermc.io/repository/maven-public/") {
            content { includeGroupByRegex("io\\.papermc.*") }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "AuthSecured"

include("core")
include("database")
include("platform-common")
include("platform-paper")
include("platform-fabric")
