#!/usr/bin/env bash
set -euo pipefail
version="${1:?release version required}"
repository="${2:-https://packages.fluxzero.io/maven}"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]
project_root="$(cd "$(dirname "$0")/../.." && pwd)"
consumer_dir="$(mktemp -d)"
trap 'rm -rf "$consumer_dir"' EXIT
cat > "$consumer_dir/pom.xml" <<'XML'
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example</groupId><artifactId>doclet-consumer</artifactId><version>1</version>
  <dependencies><dependency><groupId>io.fluxzero.tools</groupId><artifactId>json-doclet</artifactId>
    <version>${doclet.version}</version></dependency></dependencies>
  <repositories><repository><id>fluxzero</id><url>${doclet.repository}</url></repository></repositories>
</project>
XML
"$project_root/mvnw" -B -f "$consumer_dir/pom.xml" \
  "-Dmaven.repo.local=$consumer_dir/cache" "-Ddoclet.repository=$repository" "-Ddoclet.version=$version" \
  org.apache.maven.plugins:maven-dependency-plugin:3.11.0:copy-dependencies \
  -DincludeArtifactIds=json-doclet "-DoutputDirectory=$consumer_dir/lib"
artifact_dir="$consumer_dir/cache/io/fluxzero/tools/json-doclet/$version"
python3 "$project_root/.github/scripts/verify_maven_origin.py" \
  "$artifact_dir/_remote.repositories" "$version" "$repository"
cat > "$consumer_dir/Example.java" <<'JAVA'
/** Independent published doclet consumer. */
public class Example {
    /** Example value. */
    public String value;
}
JAVA
javadoc -quiet -doclet io.fluxzero.tools.jsondoclet.JsonDoclet \
  -docletpath "$consumer_dir/lib/json-doclet-$version.jar" \
  -d "$consumer_dir/docs" "$consumer_dir/Example.java"
python3 - "$consumer_dir/docs" <<'PY'
from pathlib import Path
import json, sys
files = list(Path(sys.argv[1]).rglob('*.json'))
assert files, 'No JSON documentation generated'
for file in files:
    json.loads(file.read_text())
assert any('Example' in file.read_text() for file in files), 'Example is missing from generated documentation'
print('Verified independent JSON Doclet consumer and Packages provenance')
PY
