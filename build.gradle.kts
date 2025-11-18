plugins {
    java
    `maven-publish`
    signing
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

    repositories {
        maven {
            name = "OSSRH"
            val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials {
                username = System.getenv("OSSRH_USERNAME") ?: findProperty("ossrhUsername") as String?
                password = System.getenv("OSSRH_PASSWORD") ?: findProperty("ossrhPassword") as String?
            }
        }
    }
}

signing {
    // Use environment variables for signing in CI
    val signingKey = System.getenv("OSSRH_SIGNING_KEY")
    val signingPassword = System.getenv("OSSRH_SIGNING_PASSPHRASE")

    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
    }

    sign(publishing.publications["mavenJava"])
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("jsondoclet.updateExpected", System.getProperty("jsondoclet.updateExpected", "false"))
}
