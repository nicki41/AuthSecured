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
    relocate("org.sqlite", "com.example.authsecured.libs.sqlite")
    relocate("org.flywaydb", "com.example.authsecured.libs.flyway")
    relocate("redis.clients.jedis", "com.example.authsecured.libs.jedis")
    relocate("org.apache.commons.pool2", "com.example.authsecured.libs.commons.pool2")
    relocate("org.yaml.snakeyaml", "com.example.authsecured.libs.snakeyaml")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
