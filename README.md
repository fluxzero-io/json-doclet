# JSON Doclet

JSON Doclet is a custom [Javadoc doclet](https://docs.oracle.com/javase/8/docs/technotes/guides/javadoc/doclet/overview.html) that emits structured JSON instead of the traditional HTML output. It lets you capture comprehensive documentation metadata about packages, types, and all member kinds, unlocking downstream integrations such as documentation portals, static analysis, or custom developer tooling.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).

## Features

- Full coverage of the standard Javadoc surface: packages, classes/interfaces/enums/records, nested types, constructors, methods, fields, enum constants, and record components.
- Preservation of modifiers, annotations (with element values), type parameters, inheritance hierarchy, thrown types, and constant values.
- Configurable pretty-printing and output directory via standard doclet options.
- Deterministic per-directory `index.json` files to make the output tree easy to navigate.
- Simple smoke test harness with golden JSON fixtures.

## Getting Started

### Prerequisites

- JDK 17 or newer (the doclet uses the modern `jdk.javadoc.doclet` API and records).
- Maven 3.8+

### Build

```bash
mvn clean package
```

The build produces a jar containing the doclet under `target/`.

### GitHub Releases

Commits on `main` trigger a GitHub Actions workflow that:

- Analyses commits with `mathieudutour/github-tag-action`, bumping the patch version by default and the minor version when a `feat:` commit has landed since the previous release.
- Builds the jar with that version embedded.
- Creates a Git tag and GitHub Release containing the changelog and attaches both the doclet jar and JSON schema assets.

You can download the latest release from the **Releases** page or programmatically in other workflows, for example:

```yaml
- name: Download latest JSON Doclet
  uses: robinraju/release-downloader@v1
  with:
    repository: <owner>/json-doclet
    latest: true
    fileName: json-doclet-*.jar
    out-file-path: ./lib
```

To run locally with a specific version, grab the jar from the release and invoke `javadoc` as shown below.

#### Required GitHub Secrets for Publishing

The release workflow publishes signed artifacts, sources and Javadoc to Fluxzero Packages. For releases started before 1 October 2026, it then uses the Central Publishing Maven Plugin to publish to Maven Central. Configure these GitHub repository secrets:

1. **OSSRH_USERNAME**: Your Sonatype Central Portal username (token username)
2. **OSSRH_PASSWORD**: Your Sonatype Central Portal password (token password)
3. **OSSRH_SIGNING_PASSPHRASE**: The passphrase for your GPG key
4. **OSSRH_SIGNING_KEY**: Your GPG private key in ASCII-armored format

For Maven Central publishing before the cutoff and pre-cutoff recovery:

1. Create an account at https://central.sonatype.com
2. Verify your namespace (e.g., `io.fluxzero.tools`)
3. Generate a user token at https://central.sonatype.com/account
4. Generate a GPG key pair:
   ```bash
   gpg --gen-key
   ```
5. Upload your public key to a key server:
   ```bash
   gpg --keyserver keys.openpgp.org --send-keys YOUR_KEY_ID
   ```
6. Export your private key in ASCII-armored format:
   ```bash
   gpg --armor --export-secret-keys YOUR_KEY_ID
   ```
7. Add all secrets to your GitHub repository under Settings > Secrets and variables > Actions

For more details, see the [Central Portal Guide](https://central.sonatype.org/register/central-portal/).

Fluxzero Packages needs no long-lived publishing secret. The build job requests a
short-lived GitHub OIDC token immediately before deployment, with audience
`https://packages.fluxzero.io/publish/maven` and `id-token: write`. Maven server
`fluxzero` uses username `github-actions` and that masked token as its password.
The existing signing secrets supply `MAVEN_GPG_KEY` and `MAVEN_GPG_PASSPHRASE`
to the Bouncy Castle signer in both publication steps.

`./mvnw -P sign deploy` uploads to Fluxzero Packages;
`./mvnw -P sign,central deploy` publishes to Central with automatic publication
and completion polling for pre-cutoff recovery. Releases and tags are immutable: after a partial failure,
recover using the original artifacts or publish a new version; do not rebuild and
overwrite an existing release.

### Maven repositories

Each release publishes under the coordinates `io.fluxzero.tools:json-doclet` to
Fluxzero Packages. Releases started before 1 October 2026 are also published to Maven
Central; existing Central releases remain available afterward. Downloads use
`https://packages.fluxzero.io/maven`; `/publish/maven` is only for uploads.

Gradle (Kotlin DSL):

```kotlin
repositories {
    maven { url = uri("https://packages.fluxzero.io/maven") }
    mavenCentral()
}

dependencies {
    implementation("io.fluxzero.tools:json-doclet:<version>")
}
```

Maven:

```xml
<repositories>
  <repository>
    <id>fluxzero</id>
    <url>https://packages.fluxzero.io/maven</url>
    <snapshots><enabled>false</enabled></snapshots>
  </repository>
</repositories>
<dependencies>
  <dependency>
    <groupId>io.fluxzero.tools</groupId>
    <artifactId>json-doclet</artifactId>
    <version>{version}</version>
  </dependency>
</dependencies>
```

### Running the Doclet

Invoke the JDK `javadoc` tool and point it at the doclet jar:

```bash
javadoc \
  -docletpath target/json-doclet-0.1.0-SNAPSHOT.jar \
  -doclet io.fluxzero.tools.jsondoclet.JsonDoclet \
  -d target/json-docs \
  --pretty \
  --include-private \
  $(find src/main/java -name '*.java')
```

Key options:

- `-d <dir>` (standard Javadoc option): output directory for generated JSON (defaults to `target/json-doclet`).
- `--pretty`: enables pretty-printed JSON (otherwise compact).
- `--include-private`: include private members in the output (defaults to public and protected only).

### Gradle Integration

You can integrate the doclet in a Gradle Java project by configuring the `javadoc` task, e.g.:

```kotlin
tasks.javadoc {
    val docletJar = tasks.named<Jar>("jar")
    options.docletpath = files(docletJar)
    options.doclet = "io.fluxzero.tools.jsondoclet.JsonDoclet"
    options.addStringOption("--pretty", "")
    options.addStringOption("-d", layout.buildDirectory.dir("json-docs").get().asFile.absolutePath)
}
```

Ensure the doclet jar is on the task classpath (`dependsOn(docletJar)` if necessary).

### Maven Integration

For Maven builds, wire the doclet into the `maven-javadoc-plugin`. Point the plugin at the jar you built or downloaded from a release (stored here under `tools/`):

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-javadoc-plugin</artifactId>
      <version>3.6.3</version>
      <configuration>
        <doclet>io.fluxzero.tools.jsondoclet.JsonDoclet</doclet>
        <docletPath>${project.basedir}/tools/json-doclet-0.1.0.jar</docletPath>
        <additionalOptions>
          <additionalOption>--pretty</additionalOption>
          <additionalOption>-d</additionalOption>
          <additionalOption>${project.build.directory}/json-docs</additionalOption>
        </additionalOptions>
      </configuration>
      <executions>
        <execution>
          <goals>
            <goal>javadoc</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

Adjust the jar path and options to match your build; if you publish the doclet to an artifact repository you can replace `docletPath` with `<docletArtifact>` coordinates instead. The jar is self-contained and has no runtime dependencies.

### Testing

A smoke test lives at `src/test/java/io/fluxzero/jsondoclet/JsonDocletSmokeTest.java`. To run the test suite:

```bash
mvn test
```

If you intentionally change the JSON schema, regenerate the golden fixtures with:

```bash
mvn test -Djsondoclet.updateExpected=true
```

Review the resulting diffs under `src/test/resources/example/expected` and commit as needed.

### Example GitHub Action Usage

To consume the latest JSON Doclet release in another GitHub Actions workflow, download the jar and run `javadoc` with the `-docletpath` flag. Example:

```yaml
jobs:
  docs:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout project
        uses: actions/checkout@v4

      - name: Download latest JSON Doclet
        uses: robinraju/release-downloader@v1
        with:
          repository: <owner>/json-doclet
          latest: true
          fileName: json-doclet-*.jar
          out-file-path: ./tools

      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - name: Generate JSON docs
        run: |
          DOCLET_JAR=$(ls tools/json-doclet-*.jar | head -n 1)
          javadoc \
            -docletpath "$DOCLET_JAR" \
            -doclet io.fluxzero.tools.jsondoclet.JsonDoclet \
            -d build/json-docs \
            --pretty \
            $(find src/main/java -name '*.java')
```

This pattern ensures your CI always uses the latest released doclet when generating JSON documentation.

## Directory Layout

- `src/main/java/io/fluxzero/jsondoclet`: doclet entry point, traversal logic, and serialization models.
- `src/test/resources/example/source`: sample input sources used by the smoke test.
- `src/test/resources/example/expected`: expected JSON output used for regression checking.

## Roadmap

- Rich DocComment parsing (`summary`, `description`, `@param`, `@throws`, etc.).
- Better typing for annotation values and constant expressions.
- Kotlin/Gradle plugin to streamline adoption in larger builds.

Contributions and feedback are welcome!
