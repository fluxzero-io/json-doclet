"""Release failure boundaries without GitHub writes or live uploads."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import HTTPError

spec = importlib.util.spec_from_file_location('release', Path(__file__).with_name('release.py'))
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)
VERSION = '0.0.15'
SOURCE = 'a' * 40


class VersionsTest(unittest.TestCase):
    def test_skip_orphaned_partial_publication_not_listed_in_metadata(self):
        existing = {release.artifact_paths('0.0.14')[1]: b'existing jar'}
        self.assertEqual('0.0.15', release.choose_version('0.0.14', ['v0.0.13'], existing.get))

    def test_signature_or_checksum_alone_reserves_version(self):
        for suffix in ('.asc', '.sha256'):
            with self.subTest(suffix=suffix):
                existing = {release.artifact_paths('0.0.14')[0] + suffix: b'partial'}
                self.assertEqual('0.0.15', release.choose_version('0.0.14', [], existing.get))

    def test_failed_reserved_tags_are_never_reused(self):
        self.assertEqual('0.0.16', release.choose_version('0.0.14', ['v0.0.15', 'v0.0.13'], lambda _: None))

    def test_new_manual_run_without_a_version_suggestion_uses_next_unreserved_patch(self):
        self.assertEqual('0.0.16', release.choose_version('', ['v0.0.15'], lambda _: None))

    def test_feature_suggestion_keeps_minor_bump(self):
        self.assertEqual('0.1.0', release.choose_version('0.1.0', ['v0.0.15'], lambda _: None))

    def test_packages_requests_identify_the_release_client(self):
        with patch.object(release, 'urlopen') as fetch:
            fetch.return_value.__enter__.return_value.read.return_value = b'published'
            self.assertEqual(b'published', release.remote_bytes('example'))
            request = fetch.call_args.args[0]
            self.assertEqual(release.DOWNLOAD + 'example', request.full_url)
            self.assertEqual('fluxzero-json-doclet-release/1.0', request.get_header('User-agent'))

    def test_lookup_errors_do_not_mean_version_is_free(self):
        with patch.object(release, 'urlopen', side_effect=HTTPError('url', 503, 'unavailable', {}, None)):
            with self.assertRaisesRegex(RuntimeError, '503'):
                release.choose_version(VERSION, [])
        with patch.object(release, 'urlopen', side_effect=HTTPError('url', 404, 'missing', {}, None)):
            self.assertIsNone(release.remote_bytes('example'))

    def test_rejects_unstable_or_unsafe_versions(self):
        for value in ('1.0.0-SNAPSHOT', '../1.0.0', '01.0.0', '1.0', '1.0.0\n'):
            with self.subTest(version=value), self.assertRaises(ValueError):
                release.choose_version(value, [], lambda _: None)

    def test_new_run_reserves_new_tag_but_same_run_recovers_original(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            remote = root / 'remote.git'
            work = root / 'work'
            def git(*args, cwd=None):
                return subprocess.check_output(['git', *args], cwd=cwd, text=True, stderr=subprocess.DEVNULL).strip()
            git('init', '--bare', str(remote))
            git('clone', str(remote), str(work))
            git('config', 'user.name', 'Release Test', cwd=work)
            git('config', 'user.email', 'release@example.test', cwd=work)
            git('config', 'commit.gpgsign', 'false', cwd=work)
            git('config', 'tag.gpgsign', 'false', cwd=work)
            (work / 'source').write_text('qualified source')
            git('add', '.', cwd=work)
            git('commit', '-m', 'chore: initial', cwd=work)
            source = git('rev-parse', 'HEAD', cwd=work)
            previous = Path.cwd()
            try:
                os.chdir(work)
                with patch.dict(os.environ, {'GITHUB_OUTPUT': str(root / 'output')}), patch.object(
                        release, 'choose_version', side_effect=[VERSION, '0.0.16']) as choose:
                    release.reserve('0.0.14', '101', source)
                    release.reserve('0.0.14', '101', source)
                    self.assertEqual(1, choose.call_count)
                    release.reserve('0.0.14', '102', source)
                self.assertEqual(['v0.0.15', 'v0.0.16'], git('tag').splitlines())
                self.assertIn('GitHub run: 101', git('tag', '--list', 'v0.0.15', '--format=%(contents)'))
                with self.assertRaisesRegex(RuntimeError, 'does not match'):
                    release.reserve(VERSION, '101', 'b' * 40)
            finally:
                os.chdir(previous)


class PublicationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.repository = self.root / 'repository'
        for path in release.artifact_paths(VERSION):
            for suffix in ('', '.asc'):
                file = self.repository / (path + suffix)
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_bytes((path + suffix).encode())
        self.schema = self.root / 'schema.json'
        self.schema.write_text('{}')
        self.bundle = self.root / 'publication'
        release.bundle(self.repository, self.bundle, VERSION, SOURCE, self.schema)

    def test_saved_files_can_resume_partial_publication_unchanged(self):
        paths = release.artifact_paths(VERSION)
        remote = {paths[1]: (self.bundle / paths[1]).read_bytes()}
        release.preflight(self.bundle, VERSION, SOURCE, remote.get)
        manifest = release.validate_bundle(self.bundle, VERSION, SOURCE)
        self.assertEqual(8, len(manifest['files']))

    def test_conflicting_existing_bytes_fail_before_upload(self):
        paths = release.artifact_paths(VERSION)
        with self.assertRaisesRegex(ValueError, 'different immutable bytes'):
            release.preflight(self.bundle, VERSION, SOURCE, {paths[1] + '.asc': b'new signature'}.get)

    def test_changed_saved_files_or_identity_are_rejected(self):
        with self.assertRaisesRegex(ValueError, 'identity mismatch'):
            release.validate_bundle(self.bundle, VERSION, 'b' * 40)
        path = self.bundle / release.artifact_paths(VERSION)[0]
        path.write_bytes(b'rebuilt POM')
        with self.assertRaisesRegex(ValueError, 'bytes changed'):
            release.validate_bundle(self.bundle, VERSION, SOURCE)

    def test_path_traversal_in_saved_inventory_is_rejected(self):
        path = self.bundle / 'manifest.json'
        manifest = json.loads(path.read_text())
        manifest['files']['../../unexpected'] = 'a' * 64
        path.write_text(json.dumps(manifest))
        with self.assertRaisesRegex(ValueError, 'inventory'):
            release.validate_bundle(self.bundle, VERSION, SOURCE)

    def test_missing_signatures_cannot_be_packaged(self):
        (self.repository / (release.artifact_paths(VERSION)[0] + '.asc')).unlink()
        with self.assertRaisesRegex(ValueError, 'Missing signed release file'):
            release.bundle(self.repository, self.root / 'other', VERSION, SOURCE, self.schema)

    def test_deploy_file_keeps_all_four_original_files_and_signatures(self):
        args = release.publish_arguments(self.bundle, VERSION)
        self.assertNotIn('deploy', args)
        self.assertNotIn('-Psign', args)
        options = dict(arg[2:].split('=', 1) for arg in args if arg.startswith('-D'))
        files = options['files'].split(',')
        self.assertEqual(6, len(files))
        self.assertEqual(6, len(options['types'].split(',')))
        self.assertEqual(6, len(options['classifiers'].split(',')))
        self.assertTrue(all(Path(file).is_file() for file in files))
        self.assertEqual('false', options['generatePom'])
        self.assertEqual(release.UPLOAD, options['url'])

    def test_github_existing_asset_mismatch_is_never_overwritten(self):
        assets = self.root / 'assets'
        assets.mkdir()
        (assets / 'doclet.jar').write_bytes(b'qualified bytes')
        response = subprocess.CompletedProcess([], 0, json.dumps({
            'draft': False, 'assets': [{'name': 'doclet.jar'}]}), '')
        def command(args, **kwargs):
            if args[:2] == ['gh', 'api']:
                return response
            if args[:3] == ['gh', 'release', 'download']:
                (Path(args[args.index('--dir') + 1]) / 'doclet.jar').write_bytes(b'other bytes')
                return subprocess.CompletedProcess(args, 0)
            self.fail(f'Unexpected mutation: {args}')
        with patch.dict(os.environ, {'GITHUB_REPOSITORY': 'example/doclet'}), patch.object(
                release, 'run', return_value=SOURCE), patch.object(release.subprocess, 'run', side_effect=command):
            with self.assertRaisesRegex(ValueError, 'different release bytes'):
                release.github_release(assets, VERSION, SOURCE)


if __name__ == '__main__':
    unittest.main()
