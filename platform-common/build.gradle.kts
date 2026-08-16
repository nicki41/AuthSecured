plugins {
    `java-library`
}

dependencies {
    api(project(":core"))
    implementation(project(":database"))
    implementation("redis.clients:jedis:5.1.2")
}
