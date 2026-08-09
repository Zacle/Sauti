# PostgreSQL backup and restore runbook

## Objective

Recover Sauti data from an encrypted off-site backup without writing to the
production Neon database. A successful drill proves that the dump exists, its
checksum is valid, PostgreSQL can read it, an empty isolated database accepts
the restore, and required Sauti tables can be queried afterward.

## Safety contract

- Never place production credentials in `.env.restore-drill`.
- The restore target must be disposable and empty.
- `restore-drill-postgres.sh` compares the target with the production database,
  requires the explicit `isolated-restore-drill` confirmation, requires a
  database name containing `drill`, `restore`, `test`, or `staging`, and refuses
  any target with an existing public table.
- The script never drops a database, schema, or table. It leaves the isolated
  target available for review; remove it later through the database provider.
- Do not paste environment files, database URLs, or Restic credentials into a
  ticket, log, GitHub comment, or drill evidence.

## One-time off-site setup without VPS access

Choose any Restic-supported encrypted repository. S3-compatible object storage
is shown because it keeps Sauti independent of one storage vendor.

Add these encrypted GitHub Actions secrets in **Settings → Secrets and
variables → Actions**:

- `BACKUP_RESTIC_REPOSITORY`
- `BACKUP_RESTIC_PASSWORD`
- `BACKUP_AWS_ACCESS_KEY_ID`
- `BACKUP_AWS_SECRET_ACCESS_KEY`

Then manually run `Backup verification and recovery drill` with
`configure_offsite`. The workflow sends a mode-600 environment file over the
existing deployment SSH channel without printing its contents, initializes the
repository when necessary, creates a fresh production dump, uploads it, and
verifies the off-site copy. No interactive VPS session is required.

Keep the Restic password in a second secure location. Losing it makes the
encrypted off-site backups unrecoverable.

The existing nightly cron continues to run `/opt/sauti/backup-postgres.sh`.
Each run now writes a temporary dump, validates its catalog, atomically exposes
the completed dump, writes a SHA-256 sidecar, and uploads both files when
`.env.backup-offsite` is configured.

## Daily verification

After the first successful off-site upload, set the GitHub repository variable
`BACKUP_VERIFICATION_ENABLED=true`. The `Backup verification and recovery
drill` workflow then runs `verify_offsite` every day. It restores the latest
Restic snapshot into a temporary server directory, verifies the checksum and
dump catalog, and rejects a backup older than 36 hours. It never connects to a
database. Until that variable is enabled, scheduled runs safely skip while
manual verification remains available.

Local verification remains available for diagnosis:

```bash
cd /opt/sauti
bash ./verify-postgres-backup.sh local
```

## Restore drill

1. Create a new empty, isolated PostgreSQL database or Neon branch. Its database
   name must contain `drill`, `restore`, `test`, or `staging`.
2. Add its complete connection URL as the encrypted GitHub Actions secret
   `RESTORE_DRILL_DATABASE_URL`. Do not reuse the production URL.
3. In GitHub Actions, manually run `Backup verification and recovery drill`
   with `restore_offsite`. The workflow securely installs the private mode-600
   restore configuration on the VPS before running the guarded drill.
4. Confirm the workflow output reports `status: passed`, and review the evidence
   stored under `/opt/sauti/restore-drills/restore-drill-*.json`.
5. Confirm the restored Flyway migration, tenant, call, and booking counts are
   plausible relative to production monitoring. Evidence contains counts and a
   checksum, never credentials or customer rows.
6. Remove the isolated database through its provider after review, then run the
   workflow once more with `clear_restore_config` to remove its URL from the
   VPS. You can also delete `RESTORE_DRILL_DATABASE_URL` from GitHub Secrets.

## Failure response

- Missing or stale off-site snapshot: inspect the nightly cron and Restic
  configuration; preserve the latest valid local dump until off-site replication
  is restored.
- Checksum or catalog failure: quarantine that dump and retry from an earlier
  Restic snapshot. Do not delete the last known-good snapshot.
- Restore failure: preserve the workflow output and isolated database, check the
  PostgreSQL version/extension error, and open a Sauti reliability incident.
- Validation failure: do not treat the drill as complete even if `pg_restore`
  exited successfully.
