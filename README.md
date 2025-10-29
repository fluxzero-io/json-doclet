# JSON Doclet

JSON Doclet is a custom [Javadoc doclet](https://docs.oracle.com/javase/8/docs/technotes/guides/javadoc/doclet/overview.html) that emits structured JSON instead of the traditional HTML output. It lets you capture comprehensive documentation metadata about packages, types, and all member kinds, unlocking downstream integrations such as documentation portals, static analysis, or custom developer tooling.

## Features

- Full coverage of the standard Javadoc surface: packages, classes/interfaces/enums/records, nested types, constructors, methods, fields, enum constants, and record components.
- Preservation of modifiers, annotations (with element values), type parameters, inheritance hierarchy, thrown types, and constant values.
- Configurable pretty-printing and output directory via standard doclet options.
- Deterministic per-directory `index.json` files to make the output tree easy to navigate.
- Simple smoke test harness with golden JSON fixtures.

## Getting Started

### Prerequisites

- JDK 17 or newer (the doclet uses the modern `jdk.javadoc.doclet` API and records).
- Gradle 8+ (a wrapper script is provided).

### Build

```bash
./gradlew build
```

The build produces a jar containing the doclet under `build/libs/`.

### Running the Doclet

Invoke the JDK `javadoc` tool and point it at the doclet jar:

```bash
javadoc \
  -docletpath build/libs/json-doclet-0.1.0-SNAPSHOT.jar \
  -doclet io.fluxzero.jsondoclet.JsonDoclet \
  -d build/json-docs \
  --pretty \
  --include-private \
  $(find src/main/java -name '*.java')
```

Key options:

- `-d <dir>` (standard Javadoc option): output directory for generated JSON (defaults to `build/json-doclet`).
- `--pretty`: enables pretty-printed JSON (otherwise compact).
- `--include-private`: include private members in the output (defaults to public and protected only).

### Gradle Integration

You can integrate the doclet in a Gradle Java project by configuring the `javadoc` task, e.g.:

```kotlin
tasks.javadoc {
    val docletJar = tasks.named<Jar>("jar")
    options.docletpath = files(docletJar)
    options.doclet = "io.fluxzero.jsondoclet.JsonDoclet"
    options.addStringOption("--pretty", "")
    options.addStringOption("-d", layout.buildDirectory.dir("json-docs").get().asFile.absolutePath)
}
```

Ensure the doclet jar is on the task classpath (`dependsOn(docletJar)` if necessary).

### Testing

A smoke test lives at `src/test/java/io/fluxzero/jsondoclet/JsonDocletSmokeTest.java`. To run the test suite:

```bash
./gradlew test
```

If you intentionally change the JSON schema, regenerate the golden fixtures with:

```bash
./gradlew test -Djsondoclet.updateExpected=true
```

Review the resulting diffs under `src/test/resources/example/expected` and commit as needed.

## Directory Layout

- `src/main/java/io/fluxzero/jsondoclet`: doclet entry point, traversal logic, and serialization models.
- `src/test/resources/example/source`: sample input sources used by the smoke test.
- `src/test/resources/example/expected`: expected JSON output used for regression checking.

## Roadmap

- Rich DocComment parsing (`summary`, `description`, `@param`, `@throws`, etc.).
- Better typing for annotation values and constant expressions.
- Kotlin/Gradle plugin to streamline adoption in larger builds.

Contributions and feedback are welcome!
