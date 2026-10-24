"""Prepare an isolated, reproducible Vercel release; never deploy or push automatically."""
from __future__ import annotations
import argparse
import io
import json
from pathlib import Path
import shutil
import subprocess
import tarfile

PROJECT_IDS = {'halfopen.dev': 'prj_oc1JxTawKWAF4XA0U2hHt7BhRCdM', 'baikai.site': 'prj_6VDe1P39HG9MF0kEj2MnajNA1opa'}
ORG_ID = "team_ta1uqmdeZaYrf9VDdGSrLDgz"

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("domain", choices=tuple(PROJECT_IDS))
    parser.add_argument("--source", type=Path, required=True, help="Approved release repository checkout")
    parser.add_argument("--output", type=Path, required=True, help="New isolated output directory")
    args = parser.parse_args()
    source = args.source.resolve()
    output = args.output.resolve()
    project = Path(__file__).resolve().parents[1]
    top = subprocess.check_output(["git", "-C", str(source), "rev-parse", "--show-toplevel"], text=True).strip()
    if Path(top).resolve() != source:
        parser.error("Source must be the release repository root, not a parent workspace")
    if output.exists() or output.is_relative_to(source) or output.is_relative_to(project):
        parser.error("Output must be a new directory outside both source and local project")
    sha = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    archive = subprocess.check_output(["git", "-C", str(source), "archive", "--format=tar", sha])
    output.mkdir(parents=True)
    with tarfile.open(fileobj=io.BytesIO(archive), mode="r:") as tar:
        tar.extractall(output, filter="data")
    # Overlay only reviewed hosting helpers; other uncommitted business changes are excluded.
    (output / "deploy").mkdir(exist_ok=True)
    shutil.copy2(project / "deploy/domain_variant.py", output / "deploy/domain_variant.py")
    config = project / "vercel.json"
    old_config = project / "deploy/vercel.baikai.json"
    if args.domain == "baikai.site" and old_config.exists():
        config = old_config
    shutil.copy2(config, output / "vercel.json")
    for helper in (".vercelignore", "deploy/build_vercel_frontend.py"):
        if (project / helper).is_file():
            shutil.copy2(project / helper, output / helper)
    (output / ".vercel").mkdir(exist_ok=True)
    (output / ".vercel/project.json").write_text(json.dumps({"orgId": ORG_ID, "projectId": PROJECT_IDS[args.domain]}), encoding="utf-8")
    print(f"Prepared {sha} for {args.domain}: {PROJECT_IDS[args.domain]}")
    print(f"Review {output}, then run: vercel deploy --prod --cwd \"{output}\"")
    print("This script does not deploy, commit, push, configure secrets or enable Git automation.")

if __name__ == "__main__":
    main()
