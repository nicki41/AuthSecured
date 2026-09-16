import org.gradle.api.file.DuplicatesStrategy

plugins {
    java
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":database"))
    implementation(project(":platform-common"))

    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    compileOnly("org.apache.logging.log4j:log4j-core:2.26.1") { isTransitive = false }
    testImplementation("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveBaseName.set("authsecured-paper")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    minimize {
        // JDBC drivers, Flyway providers and serializers use reflection or ServiceLoader.
        exclude(dependency("org.xerial:sqlite-jdbc:.*"))
        exclude(dependency("org.postgresql:postgresql:.*"))
        exclude(dependency("org.flywaydb:.*:.*"))
        exclude(dependency("com.zaxxer:HikariCP:.*"))
        exclude(dependency("redis.clients:jedis:.*"))
        exclude(dependency("org.yaml:snakeyaml:.*"))
        exclude(dependency("com.google.code.gson:gson:.*"))
        exclude(dependency("org.json:json:.*"))
    }
    exclude("META-INF/LICENSE*", "META-INF/NOTICE*")
    exclude("org/checkerframework/**") // Compile-time annotations only.

    relocate("org.bouncycastle", "io.github.touchfalls.authsecured.libs.bouncycastle")
    relocate("com.zaxxer.hikari", "io.github.touchfalls.authsecured.libs.hikari")
    relocate("org.postgresql", "io.github.touchfalls.authsecured.libs.postgresql")
    relocate("org.flywaydb", "io.github.touchfalls.authsecured.libs.flyway")
    relocate("redis.clients.jedis", "io.github.touchfalls.authsecured.libs.jedis")
    relocate("org.apache.commons.pool2", "io.github.touchfalls.authsecured.libs.commons.pool2")
    relocate("org.yaml.snakeyaml", "io.github.touchfalls.authsecured.libs.snakeyaml")
    relocate("com.google.gson", "io.github.touchfalls.authsecured.libs.gson")
    relocate("org.json", "io.github.touchfalls.authsecured.libs.json")
    // sqlite-jdbc must keep org.sqlite: its JNI bridge and native resource lookup
    // use the original package and break when only bytecode names are relocated.
}

val verifySQLiteDriverInShadowJar by tasks.registering {
    dependsOn(tasks.shadowJar)
    doLast {
        val archive = tasks.shadowJar.get().archiveFile.get().asFile
        val nativeDb = zipTree(archive).matching { include("org/sqlite/core/NativeDB.class") }
        check(!nativeDb.isEmpty) { "SQLite NativeDB is missing from ${archive.name}" }
        check(!zipTree(archive).matching {
            include("io/github/touchfalls/authsecured/AuthSecuredPlugin.class")
        }.isEmpty) { "Paper entrypoint is missing from ${archive.name}" }
        check(zipTree(archive).matching { include("com/google/gson/**", "org/json/**") }.isEmpty) {
            "Unrelocated JSON libraries remain in ${archive.name}"
        }
    }
}

tasks.build {
    dependsOn(tasks.shadowJar, verifySQLiteDriverInShadowJar)
}
