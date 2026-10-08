import tempfile
from pathlib import Path
import unittest

from verify_maven_origin import verify_origin


REPOSITORY = "file:///tmp/release-fixture/replayed"
QUALIFIED_ORIGIN = "fluxzero-5d6b6a150533c3a6e7e6aa39a1aed9f1667dc163"


class MavenOriginTest(unittest.TestCase):
    def check(self, entries, repository=REPOSITORY):
        with tempfile.TemporaryDirectory() as temporary:
            tracking = Path(temporary) / "_remote.repositories"
            tracking.write_text("# Maven Resolver internal tracking\n" + "\n".join(entries) + "\n")
            verify_origin(tracking, "0.0.0", repository)

    def entries(self, origin):
        return [f"json-doclet-0.0.0.{ext}>{origin}=" for ext in ("jar", "pom")]

    def test_legacy_maven_origin(self):
        self.check(self.entries("fluxzero"))

    def test_url_qualified_maven_origin(self):
        self.check(self.entries(QUALIFIED_ORIGIN))

    def test_same_repository_id_at_another_url_is_rejected(self):
        with self.assertRaises(ValueError):
            self.check(self.entries(QUALIFIED_ORIGIN), "https://other.example.invalid/maven")

    def test_arbitrary_qualified_origin_is_rejected(self):
        with self.assertRaises(ValueError):
            self.check(self.entries("fluxzero-" + "0" * 40))

    def test_central_and_local_install_origins_are_rejected(self):
        for origin in ("central", ""):
            with self.subTest(origin=origin), self.assertRaises(ValueError):
                self.check(self.entries(origin))

    def test_both_jar_and_pom_are_required(self):
        with self.assertRaises(ValueError):
            self.check(self.entries(QUALIFIED_ORIGIN)[:1])

    def test_other_artifact_and_mixed_origins_are_rejected(self):
        cases = [
            [line.replace("json-doclet-0.0.0.", "other-json-doclet-0.0.0.")
             for line in self.entries(QUALIFIED_ORIGIN)],
            [self.entries(QUALIFIED_ORIGIN)[0], self.entries("fluxzero")[1]],
        ]
        for entries in cases:
            with self.subTest(entries=entries), self.assertRaises(ValueError):
                self.check(entries)


if __name__ == "__main__":
    unittest.main()
