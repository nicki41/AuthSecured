plugins {
    `java-library`
}

dependencies {
    api(project(":core"))
    implementation(project(":database"))
    implementation("redis.clients:jedis:5.2.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
}
