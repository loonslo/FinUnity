"""Build-domain URLs for a reviewed static output, without changing source content."""
from __future__ import annotations

import os
from pathlib import Path
import re
import sys

DOMAINS = ("halfopen.dev", "baikai.site")
TEXT_SUFFIXES = {".html", ".xml", ".txt", ".json", ".js", ".css", ".svg"}
PUBLIC_HOST = re.compile(
    r"(?<![\w@.-])(?P<prefix>(?:www\.|character\.|knowledge\.|finunity\.|"
    r"finunity-api\.|dashboards\.)?)(?:halfopen\.dev|baikai\.site)(?![\w.-])"
)


def apply_variant(output: Path, domain: str) -> int:
    if domain not in DOMAINS:
        raise ValueError("SITE_DOMAIN_ROOT must be halfopen.dev or baikai.site")
    if output.is_symlink() or not output.is_dir():
        raise ValueError("Expected an existing, non-symlink static output directory")
    root = output.resolve()
    changed = 0
    for path in sorted(output.rglob("*")):
        if path.is_symlink() or not path.resolve().is_relative_to(root):
            raise ValueError("Static output must not contain symlinks or escaped paths")
        if not path.is_file() or path.suffix.lower() not in TEXT_SUFFIXES:
            continue
        before = path.read_text(encoding="utf-8")
        after = PUBLIC_HOST.sub(lambda match: match["prefix"] + domain, before)
        if after != before:
            path.write_text(after, encoding="utf-8", newline="\n")
            changed += 1
    return changed


def main() -> None:
    domain = os.environ.get("SITE_DOMAIN_ROOT", "halfopen.dev")
    # Resolve the output against this project, never against an arbitrary cwd.
    root = Path(__file__).resolve().parents[1]
    output = root / sys.argv[1]
    if output.resolve().parent != root or output.name not in {"dist", "public"}:
        raise ValueError("Only this project's dist/ or public/ may be transformed")
    print(f"Domain variant {domain}: {apply_variant(output, domain)} files updated")


if __name__ == "__main__":
    main()
