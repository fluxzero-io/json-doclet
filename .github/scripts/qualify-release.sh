#!/usr/bin/env bash
# Exercise signing and immutable byte replay only against temporary file repositories.
set -euo pipefail
set +x
project_root="$(cd "$(dirname "$0")/../.." && pwd)"
qualification_dir="$(mktemp -d)"
trap 'gpgconf --homedir "$qualification_dir/gnupg" --kill all >/dev/null 2>&1 || true; rm -rf "$qualification_dir"' EXIT
mkdir -m 700 "$qualification_dir/gnupg"
python3 - "$project_root" "$qualification_dir/project" <<'PY'
from pathlib import Path
import shutil, sys
source, target = map(Path, sys.argv[1:])
target.mkdir()
for name in ('pom.xml', 'mvnw'):
    shutil.copy2(source / name, target / name)
for name in ('.mvn', 'src'):
    shutil.copytree(source / name, target / name)
PY
export GNUPGHOME="$qualification_dir/gnupg"
gpg --batch --pinentry-mode loopback --passphrase '' \
  --quick-generate-key 'Release Qualification <ci@example.invalid>' rsa2048 sign 0
export MAVEN_GPG_KEY="$(gpg --batch --armor --export-secret-keys ci@example.invalid)"
export MAVEN_GPG_PASSPHRASE=''
export RELEASE_TIMESTAMP="$(git -C "$project_root" show -s --format=%ct HEAD)"
(
  cd "$qualification_dir/project"
  ./mvnw -B versions:set -DnewVersion=0.0.0 -DgenerateBackupPoms=false
  ./mvnw -B -Psign clean deploy \
    "-DaltDeploymentRepository=fluxzero::file://$qualification_dir/repository"
  for signature in "$qualification_dir/repository/io/fluxzero/tools/json-doclet/0.0.0/"*.asc; do
    gpg --batch --verify "$signature" "${signature%.asc}"
  done
  python3 "$project_root/.github/scripts/release.py" bundle \
    --version 0.0.0 --source 0000000000000000000000000000000000000000 \
    --repository "$qualification_dir/repository" --directory "$qualification_dir/publication"
)
unset MAVEN_GPG_KEY MAVEN_GPG_PASSPHRASE
python3 - "$project_root" "$qualification_dir" <<'PY'
import importlib.util, subprocess, sys
from pathlib import Path
root, temporary = map(Path, sys.argv[1:])
spec = importlib.util.spec_from_file_location('release', root / '.github/scripts/release.py')
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)
args = release.publish_arguments(temporary / 'publication', '0.0.0')
args = ['-Durl=' + (temporary / 'replayed').as_uri() if arg.startswith('-Durl=') else arg for arg in args]
# Both attempts must publish exactly the original signed files, without compiling or signing again.
for _ in range(2):
    subprocess.run(args, cwd=temporary / 'project', check=True)
for path in release.artifact_paths('0.0.0'):
    for suffix in ('', '.asc'):
        assert (temporary / 'repository' / (path + suffix)).read_bytes() == (temporary / 'replayed' / (path + suffix)).read_bytes(), path + suffix
print('Both local publication attempts preserve every original artifact and signature')
PY
bash "$project_root/.github/scripts/verify-packages-consumer.sh" 0.0.0 "file://$qualification_dir/replayed"
