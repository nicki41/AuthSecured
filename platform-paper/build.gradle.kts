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
    exclude("META-INF/LICENSE*", "META-INF/NOTICE*")

    relocate("org.bouncycastle", "com.example.authsecured.libs.bouncycastle")
    relocate("com.zaxxer.hikari", "com.example.authsecured.libs.hikari")
    relocate("org.postgresql", "com.example.authsecured.libs.postgresql")
    relocate("org.flywaydb", "com.example.authsecured.libs.flyway")
    relocate("redis.clients.jedis", "com.example.authsecured.libs.jedis")
    relocate("org.apache.commons.pool2", "com.example.authsecured.libs.commons.pool2")
    relocate("org.yaml.snakeyaml", "com.example.authsecured.libs.snakeyaml")
}

val verifySQLiteDriverInShadowJar by tasks.registering {
    dependsOn(tasks.shadowJar)
    doLast {
        val archive = tasks.shadowJar.get().archiveFile.get().asFile
        val nativeDb = zipTree(archive).matching { include("org/sqlite/core/NativeDB.class") }
        check(!nativeDb.isEmpty) { "SQLite NativeDB is missing from ${archive.name}" }
    }
}

tasks.build {
    dependsOn(tasks.shadowJar, verifySQLiteDriverInShadowJar)
}
