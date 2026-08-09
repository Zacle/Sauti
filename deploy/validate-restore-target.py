#!/usr/bin/env python3
import sys
from pathlib import Path
from urllib.parse import urlsplit


def read_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text().splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def database_identity(raw_url: str, label: str) -> tuple[str, int, str]:
    parsed = urlsplit(raw_url)
    database = parsed.path.strip("/")
    if parsed.scheme not in {"postgres", "postgresql"} or not parsed.hostname or not database:
        raise ValueError(f"{label} must be a complete PostgreSQL URL")
    return parsed.hostname.lower(), parsed.port or 5432, database.lower()


def validate_restore_target(production: dict[str, str], drill: dict[str, str]) -> None:
    if drill.get("SAUTI_RESTORE_CONFIRM") != "isolated-restore-drill":
        raise ValueError("SAUTI_RESTORE_CONFIRM must equal isolated-restore-drill")

    source_identity = database_identity(production.get("NEON_DATABASE_URL", ""), "NEON_DATABASE_URL")
    target_identity = database_identity(
        drill.get("SAUTI_RESTORE_DATABASE_URL", ""), "SAUTI_RESTORE_DATABASE_URL"
    )
    if source_identity == target_identity:
        raise ValueError("Refusing to restore into the production database")
    if not any(marker in target_identity[2] for marker in ("drill", "restore", "test", "staging")):
        raise ValueError("Restore target database name must contain drill, restore, test, or staging")


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("Usage: validate-restore-target.py PRODUCTION_ENV RESTORE_DRILL_ENV")
    try:
        validate_restore_target(read_env(Path(sys.argv[1])), read_env(Path(sys.argv[2])))
    except (OSError, ValueError) as error:
        raise SystemExit(str(error)) from error


if __name__ == "__main__":
    main()
