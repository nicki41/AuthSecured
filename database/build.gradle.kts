plugins {
    java
}

dependencies {
    implementation(project(":core"))

    implementation("com.zaxxer:HikariCP:6.3.3")
    implementation("org.postgresql:postgresql:42.7.13")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("org.flywaydb:flyway-core:10.22.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.22.0")
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.18.10"))
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.18.10")
    implementation("redis.clients:jedis:5.2.0")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("org.json:json:20260814")
    implementation("org.slf4j:slf4j-api:2.0.12")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
}
