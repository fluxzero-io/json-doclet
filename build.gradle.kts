plugins {
    java
    `maven-publish`
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
            name = "GitHubPackages"
            val githubRepository = (findProperty("githubRepository") as String?)
                ?: System.getenv("GITHUB_REPOSITORY")
                ?: "fluxzero/json-doclet"
            url = uri("https://maven.pkg.github.com/$githubRepository")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                    ?: (findProperty("gpr.user") as String?)
                password = System.getenv("GITHUB_TOKEN")
                    ?: (findProperty("gpr.key") as String?)
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
