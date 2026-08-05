"""Repository checks that do not require private accounts or network access."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SKIP_DIRECTORIES = {".git", ".gradle", ".idea", "build", "node_modules", "__pycache__"}
REQUIRED_FILES = (
    ".editorconfig",
    ".gitattributes",
    ".github/workflows/ci.yml",
    "contracts/openapi/connexa-v1.yaml",
    "android/app/build.gradle.kts",
    "services/connexa-api/build.gradle.kts",
    "services/connexa-api/.env.example",
    "docs/architecture/foundation.md",
)
FORBIDDEN_FILE_NAMES = {
    ".env",
    "google-services.json",
    "local.properties",
}
SECRET_LITERAL_ASSIGNMENT = re.compile(
    r"""(?ix)
    (?:api[_-]?key|access[_-]?token|secret|password|private[_-]?key)
    \s*[:=]\s*
    ["']
    [A-Za-z0-9_./+=-]{8,}
    ["']
    """
)
GOOGLE_API_KEY = re.compile(r"AIza[0-9A-Za-z_-]{20,}")


def project_files() -> list[Path]:
    files: list[Path] = []
    for path in ROOT.rglob("*"):
        if any(part in SKIP_DIRECTORIES for part in path.parts):
            continue
        if path.is_file():
            files.append(path)
    return files


def tracked_relative_paths() -> set[str]:
    result = subprocess.run(
        ["git", "-C", str(ROOT), "ls-files", "-z"],
        check=False,
        capture_output=True,
    )
    if result.returncode != 0:
        return set()
    return {
        item.decode("utf-8")
        for item in result.stdout.split(b"\0")
        if item
    }


def check_required_files(errors: list[str]) -> None:
    for relative_path in REQUIRED_FILES:
        if not (ROOT / relative_path).is_file():
            errors.append(f"Missing required foundation file: {relative_path}")


def check_empty_environment_template(errors: list[str]) -> None:
    template = ROOT / "services/connexa-api/.env.example"
    if not template.is_file():
        return

    for line_number, line in enumerate(template.read_text(encoding="utf-8").splitlines(), start=1):
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        if "=" not in stripped:
            errors.append(f"Invalid environment-template line {line_number}: {stripped}")
            continue
        key, value = stripped.split("=", maxsplit=1)
        if not re.fullmatch(r"[A-Z][A-Z0-9_]*", key):
            errors.append(f"Invalid environment-template key on line {line_number}: {key}")
        if value:
            errors.append(f"Environment-template value must be empty on line {line_number}: {key}")


def check_openapi_basics(errors: list[str]) -> None:
    contract = ROOT / "contracts/openapi/connexa-v1.yaml"
    if not contract.is_file():
        return

    content = contract.read_text(encoding="utf-8")
    for expected in (
        "openapi: 3.1.1",
        "/api/v1/platform/status:",
        "/api/v1/events:",
        "application/problem+json:",
        "EventSummary:",
        "UserProfile:",
    ):
        if expected not in content:
            errors.append(f"OpenAPI contract is missing required declaration: {expected}")


def check_for_secrets_and_sensitive_files(errors: list[str]) -> None:
    tracked_paths = tracked_relative_paths()
    for path in project_files():
        relative_path = path.relative_to(ROOT)
        if path.name in FORBIDDEN_FILE_NAMES:
            if relative_path.as_posix() in tracked_paths:
                errors.append(f"Sensitive local configuration must not be tracked: {relative_path}")
            continue
        if path.suffix.lower() in {".jar", ".png", ".jpg", ".jpeg", ".gif"}:
            continue
        if path.name == ".env.example":
            continue
        try:
            content = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            errors.append(f"Unexpected non-text tracked file: {relative_path}")
            continue
        private_key_begin = "-----" + "BEGIN "
        private_key_end = "PRIVATE " + "KEY-----"
        if private_key_begin in content and private_key_end in content:
            errors.append(f"Private-key material detected: {relative_path}")
        if SECRET_LITERAL_ASSIGNMENT.search(content) or GOOGLE_API_KEY.search(content):
            errors.append(f"Possible hard-coded secret detected: {relative_path}")


def main() -> int:
    errors: list[str] = []
    check_required_files(errors)
    check_empty_environment_template(errors)
    check_openapi_basics(errors)
    check_for_secrets_and_sensitive_files(errors)

    if errors:
        print("Foundation verification failed:")
        for error in errors:
            print(f"- {error}")
        return 1

    print("Foundation verification passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
