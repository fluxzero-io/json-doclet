plugins {
    java
}

group = "io.fluxzero"

val resolvedVersion: String = (findProperty("releaseVersion")?.toString()
        ?: System.getenv("RELEASE_VERSION")
        ?: "0.1.0-SNAPSHOT")

version = resolvedVersion

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withJavadocJar()
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.everit.json:org.everit.json.schema:1.5.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("jsondoclet.updateExpected", System.getProperty("jsondoclet.updateExpected", "false"))
}
