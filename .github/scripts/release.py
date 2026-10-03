#!/usr/bin/env python3
"""Immutable JSON Doclet release reservation, saved publication and recovery."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
from urllib.error import HTTPError
from urllib.request import urlopen

DOWNLOAD = 'https://packages.fluxzero.io/maven/'
UPLOAD = 'https://packages.fluxzero.io/publish/maven'
GROUP_PATH = 'io/fluxzero/tools/json-doclet'
VERSION = re.compile(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)')
SHA = re.compile(r'[0-9a-f]{40}')


def run(*args):
    return subprocess.check_output(args, text=True).strip()


def version_tuple(version):
    if not VERSION.fullmatch(version):
        raise ValueError(f'Invalid stable release version: {version}')
    return tuple(map(int, version.split('.')))


def artifact_paths(version):
    version_tuple(version)
    prefix = f'{GROUP_PATH}/{version}/json-doclet-{version}'
    return [prefix + suffix for suffix in ('.pom', '.jar', '-sources.jar', '-javadoc.jar')]


def remote_bytes(path):
    try:
        with urlopen(DOWNLOAD + path, timeout=30) as response:
            return response.read()
    except HTTPError as error:
        if error.code == 404:
            return None
        raise RuntimeError(f'Packages lookup failed: HTTP {error.code} for {path}') from error


def occupied(version, fetch=remote_bytes):
    # Metadata can lag behind a partial upload; inspect actual immutable files.
    return any(fetch(path + suffix) is not None for path in artifact_paths(version)
               for suffix in ('', '.asc', '.md5', '.sha1', '.sha256', '.sha512'))


def choose_version(suggested, tags, fetch=remote_bytes):
    reserved = [version_tuple(t[1:]) for t in tags if t.startswith('v') and VERSION.fullmatch(t[1:])]
    candidate = version_tuple(suggested) if suggested else (max(reserved) if reserved else (0, 0, 0))
    if not suggested and not reserved:
        candidate = (0, 0, 1)
    if reserved and candidate <= max(reserved):
        major, minor, patch = max(reserved)
        candidate = (major, minor, patch + 1)
    for _ in range(100):
        version = '.'.join(map(str, candidate))
        if not occupied(version, fetch):
            return version
        print(f'Skipping occupied Packages version {version}')
        major, minor, patch = candidate
        candidate = (major, minor, patch + 1)
    raise RuntimeError('No free release version found within 100 candidates')


def reserve(suggested, run_id, source):
    if not run_id.isdigit() or not SHA.fullmatch(source):
        raise ValueError('Invalid run or source identity')
    tags = run('git', 'tag', '--list', 'v*').splitlines()
    reservations = [tag for tag in tags if f'GitHub run: {run_id}' in
                    run('git', 'tag', '--list', tag, '--format=%(contents)').splitlines()]
    if reservations:
        if len(reservations) != 1 or run('git', 'rev-parse', reservations[0] + '^{}') != source:
            raise RuntimeError('Release reservation does not match this run and source')
        version = reservations[0][1:]
        version_tuple(version)
    else:
        version = choose_version(suggested, tags)
        run('git', 'tag', '-a', 'v' + version, source, '-m',
            f'JSON Doclet {version}\n\nGitHub run: {run_id}\nSource: {source}')
        # A rejected push is a reservation failure, never permission to upload.
        run('git', 'push', 'origin', 'refs/tags/v' + version)
    with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
        output.write(f'version={version}\n')
    print(f'Reserved v{version} for {source}')


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def bundle(repository, output, version, source, schema):
    if not SHA.fullmatch(source):
        raise ValueError('Invalid source commit')
    paths = artifact_paths(version)
    required = paths + [p + '.asc' for p in paths]
    for path in required:
        if not (repository / path).is_file():
            raise ValueError(f'Missing signed release file: {path}')
    output.mkdir(parents=True)
    directory = repository / GROUP_PATH / version
    files = {}
    for source_file in sorted(directory.iterdir()):
        if source_file.is_file():
            relative = source_file.relative_to(repository).as_posix()
            target = output / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source_file, target)
            files[relative] = digest(target)
    shutil.copyfile(schema, output / 'json-doclet.schema.json')
    (output / 'manifest.json').write_text(json.dumps({
        'version': version, 'source': source, 'files': files,
        'schema_sha256': digest(output / 'json-doclet.schema.json'),
    }, indent=2) + '\n')
    validate_bundle(output, version, source)


def validate_bundle(directory, version, source):
    manifest = json.loads((directory / 'manifest.json').read_text())
    if manifest['version'] != version or manifest['source'] != source or not SHA.fullmatch(source):
        raise ValueError('Saved publication identity mismatch')
    paths = artifact_paths(version)
    allowed = {p + suffix for p in paths for suffix in
               ('', '.asc', '.md5', '.sha1', '.sha256', '.sha512',
                '.asc.md5', '.asc.sha1', '.asc.sha256', '.asc.sha512')}
    required = set(paths + [p + '.asc' for p in paths])
    if not required <= manifest['files'].keys() or not manifest['files'].keys() <= allowed:
        raise ValueError('Unexpected or incomplete saved publication inventory')
    for path, expected in manifest['files'].items():
        if digest(directory / path) != expected:
            raise ValueError(f'Saved release bytes changed: {path}')
    if digest(directory / 'json-doclet.schema.json') != manifest['schema_sha256']:
        raise ValueError('Saved schema changed')
    return manifest


def preflight(directory, version, source, fetch=remote_bytes):
    manifest = validate_bundle(directory, version, source)
    for path in artifact_paths(version):
        for suffix in ('', '.asc'):
            name = path + suffix
            existing = fetch(name)
            if existing is not None and hashlib.sha256(existing).hexdigest() != manifest['files'][name]:
                raise ValueError(f'Packages already contains different immutable bytes: {name}')


def publish_arguments(directory, version):
    paths = artifact_paths(version)
    pom, jar, sources, javadoc = [str(directory.resolve() / p) for p in paths]
    return ['./mvnw', '-B', 'org.apache.maven.plugins:maven-deploy-plugin:3.1.4:deploy-file',
            '-DrepositoryId=fluxzero', '-Durl=' + UPLOAD, '-DgeneratePom=false',
            '-Dfile=' + jar, '-DpomFile=' + pom,
            '-Dfiles=' + ','.join([sources, javadoc, pom + '.asc', jar + '.asc',
                                  sources + '.asc', javadoc + '.asc']),
            '-Dtypes=jar,jar,pom.asc,jar.asc,jar.asc,jar.asc',
            '-Dclassifiers=sources,javadoc,,,sources,javadoc',
            '-DretryFailedDeploymentCount=3']


def verify(directory, output, version, source):
    validate_bundle(directory, version, source)
    spec = importlib.util.spec_from_file_location('assets', Path(__file__).with_name('collect-maven-assets.py'))
    assets = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(assets)
    assets.collect(artifact_paths(version), output)
    for path in artifact_paths(version):
        for suffix in ('', '.asc'):
            if (output / (Path(path).name + suffix)).read_bytes() != (directory / (path + suffix)).read_bytes():
                raise ValueError(f'Published bytes differ from the saved publication: {path + suffix}')
    shutil.copyfile(directory / 'json-doclet.schema.json', output / 'json-doclet.schema.json')


def github_release(directory, version, source):
    tag = 'v' + version
    version_tuple(version)
    if run('git', 'rev-parse', tag + '^{}') != source:
        raise ValueError('Release tag no longer points to the qualified source')
    repo = os.environ['GITHUB_REPOSITORY']
    response = subprocess.run(['gh', 'api', f'repos/{repo}/releases/tags/{tag}'],
                              capture_output=True, text=True)
    files = sorted(p for p in directory.iterdir() if p.is_file())
    if not files:
        raise ValueError('No verified release assets')
    if response.returncode:
        if '(HTTP 404)' not in response.stderr:
            raise RuntimeError(response.stderr)
        subprocess.run(['gh', 'release', 'create', tag, '--repo', repo, '--verify-tag',
                        '--draft', '--title', tag, '--generate-notes', *map(str, files)], check=True)
        draft = True
    else:
        release = json.loads(response.stdout)
        draft = release['draft']
        existing = {asset['name'] for asset in release['assets']}
        with tempfile.TemporaryDirectory() as temp:
            for path in files:
                if path.name in existing:
                    subprocess.run(['gh', 'release', 'download', tag, '--repo', repo,
                                    '--pattern', path.name, '--dir', temp], check=True)
                    if digest(Path(temp) / path.name) != digest(path):
                        raise ValueError(f'GitHub already contains different release bytes: {path.name}')
                else:
                    subprocess.run(['gh', 'release', 'upload', tag, str(path), '--repo', repo], check=True)
    if draft:
        subprocess.run(['gh', 'release', 'edit', tag, '--repo', repo, '--draft=false', '--latest'], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['reserve', 'assert-unpublished', 'bundle', 'validate', 'preflight', 'publish', 'verify', 'github'])
    parser.add_argument('--version', default=os.getenv('RELEASE_VERSION'))
    parser.add_argument('--source', default=os.getenv('GITHUB_SHA'))
    parser.add_argument('--run-id', default=os.getenv('GITHUB_RUN_ID'))
    parser.add_argument('--suggested', default=os.getenv('SUGGESTED_VERSION'))
    parser.add_argument('--directory', type=Path, default=Path('publication'))
    parser.add_argument('--repository', type=Path, default=Path('target/publication-repository'))
    parser.add_argument('--output', type=Path, default=Path('release-assets'))
    args = parser.parse_args()
    if args.command == 'reserve':
        reserve(args.suggested, args.run_id, args.source)
    elif args.command == 'assert-unpublished':
        if occupied(args.version):
            raise RuntimeError('Saved publication is unavailable but Packages already contains this version; start a new run for a fresh version')
    elif args.command == 'bundle':
        bundle(args.repository, args.directory, args.version, args.source, Path('src/main/resources/json-doclet.schema.json'))
    elif args.command == 'validate':
        validate_bundle(args.directory, args.version, args.source)
    elif args.command == 'preflight':
        preflight(args.directory, args.version, args.source)
    elif args.command == 'publish':
        subprocess.run(publish_arguments(args.directory, args.version), check=True)
    elif args.command == 'verify':
        verify(args.directory, args.output, args.version, args.source)
    elif args.command == 'github':
        github_release(args.output, args.version, args.source)


if __name__ == '__main__':
    main()
