plugins {
    java
    `maven-publish`
    signing
    id("org.jreleaser") version "1.19.0"
}

group = "io.fluxzero.tools"

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

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set("json-doclet")
                description.set("A JSON Schema doclet for Javadoc")
                url.set("https://github.com/fluxzero/json-doclet")
                inceptionYear.set("2025")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("fluxzero")
                        name.set("Fluxzero")
                        email.set("info@fluxzero.io")
                    }
                }

                scm {
                    url.set("https://github.com/fluxzero/json-doclet")
                    connection.set("scm:git:git://github.com/fluxzero/json-doclet.git")
                    developerConnection.set("scm:git:ssh://git@github.com:fluxzero/json-doclet.git")
                }
            }
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("jsondoclet.updateExpected", System.getProperty("jsondoclet.updateExpected", "false"))
}
