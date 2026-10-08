# Release operation and recovery

`Build and Release` runs on `main` pushes and manual dispatches against `main`.
Runs are serialized without cancelling a running publication. Pull requests have
read-only verification and cannot publish artifacts or tags.

## Release sequence

1. Derive the semantic version from commits (patch by default, minor for `feat`).
   Skip reserved Git tags and occupied Packages paths, including orphaned files
   from a partial upload that Maven metadata does not list. Lookup failures stop
   the release; only a 404 establishes that an artifact is absent.
2. Reserve an annotated immutable `v<version>` tag on the exact source commit.
   The annotation records the GitHub run ID. Never move or delete a release tag.
3. Build, test and sign into a temporary Maven repository. Preserve the POM,
   JAR, sources, Javadoc, all signatures, checksums and schema as the
   `signed-publication` Actions artifact before any public upload. A SHA-256
   inventory binds that artifact to the source commit and reserved version.
4. Publish those saved files with Maven `deploy-file`; never rebuild or sign in
   the publishing job. Existing files must have identical bytes. Maven retains
   its normal repository metadata update behavior.
5. Download the publication from Packages, validate checksums and compare every
   artifact and signature with the saved bytes. Run an independent Javadoc
   consumer with an empty Maven cache and verify repository provenance.
6. Create or finish the GitHub Release only after Packages verification. Include
   all exact Maven files plus `json-doclet.schema.json`. An existing GitHub asset
   is compared and retained, never overwritten. New releases stay draft until
   every upload succeeds.

## Credentials

Only the version-reservation and GitHub-release jobs have `contents: write`.
Only the Packages publisher has `id-token: write`; its short-lived OIDC audience
is `https://packages.fluxzero.io/publish/maven`. Maven server `fluxzero` uses
`github-actions` and that token. No long-lived upload credential is needed.

`OSSRH_SIGNING_KEY` and `OSSRH_SIGNING_PASSPHRASE` are needed only while creating
the saved publication. They remain the artifact-signing credentials despite
the historical names. CI does not publish to Maven Central.

## Recovery

Rerunning failed jobs or all jobs of the **same run** reuses its reserved tag and
saved signed files. Partial Packages uploads can finish because every retried
file has exactly the original bytes; a mismatch stops before publication. A
partial GitHub Release similarly resumes missing asset uploads without replacing
existing files.

The independent consumer uses a fresh Maven cache and verifies both JAR and
POM origins. It recognizes legacy `fluxzero` tracking and the newer URL-qualified
`fluxzero-<sha1(repository URL)>` entries, checking the exact configured URL hash.
Unrelated repositories, missing origins and incomplete artifact pairs fail verification.

Saved publication artifacts are retained for 90 days. If they have expired and
any file is already public, the workflow refuses to rebuild that version. Start
a **new manual run** on `main` to reserve the next free version. Its new run ID
allocates a fresh version even when the source commit has not changed. Never
rewrite an occupied version to repair a failed release.

The release qualification runs locally with Java 21+, Python 3 and GnuPG:

```sh
python3 -m unittest discover -s .github/scripts -p 'test*.py'
bash .github/scripts/qualify-release.sh
```

Qualification generates a temporary signing key, builds and tests a copied
project, verifies its signatures, publishes the same signed files twice into a
second local file repository and runs an independent consumer. It neither uses
production signing secrets nor uploads to a live repository.
