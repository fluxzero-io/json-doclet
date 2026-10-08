#!/usr/bin/env python3
"""Verify both consumer artifacts against Maven's recorded repository origin."""
import hashlib
from pathlib import Path
import sys


def verify_origin(tracking_file: Path, version: str, repository_url: str) -> None:
    entries = {line.strip() for line in tracking_file.read_text().splitlines()
               if line.strip() and not line.startswith("#")}
    # Older Maven records only the repository ID. New Resolver tracking also
    # binds it to the exact URL; never accept an arbitrary fluxzero-* prefix.
    url_hash = hashlib.sha1(repository_url.encode("utf-8")).hexdigest()
    for origin in ("fluxzero", f"fluxzero-{url_hash}"):
        expected = {f"json-doclet-{version}.{ext}>{origin}=" for ext in ("jar", "pom")}
        if expected <= entries:
            return
    raise ValueError("Consumer JAR and POM lack matching Fluxzero repository provenance")


if __name__ == "__main__":
    verify_origin(Path(sys.argv[1]), sys.argv[2], sys.argv[3])
