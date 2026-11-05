#!/bin/sh
# Overrides the stock postgres image's entrypoint for the read replica only. On first boot
# (empty $PGDATA) it clones the primary via pg_basebackup instead of running initdb — -R makes
# pg_basebackup write standby.signal + primary_conninfo itself, so the normal entrypoint below
# starts postgres straight into standby/recovery mode streaming from the primary. On every
# later boot $PGDATA already has a PG_VERSION file, so this clone step is skipped entirely and
# postgres just resumes as a standby, same as the primary resuming from its own data directory.
set -e

if [ -z "$(ls -A "$PGDATA" 2>/dev/null)" ]; then
    echo "replica-entrypoint: \$PGDATA is empty — waiting for the primary, then cloning it"
    until pg_isready -h "$PRIMARY_HOST" -U "$POSTGRES_USER" -q; do
        sleep 2
    done
    pg_basebackup -h "$PRIMARY_HOST" -U "$POSTGRES_USER" -D "$PGDATA" -Fp -Xs -P -R
fi

exec docker-entrypoint.sh postgres
