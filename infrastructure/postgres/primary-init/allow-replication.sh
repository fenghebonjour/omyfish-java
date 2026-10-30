#!/bin/sh
# Runs once, during the primary's first initdb (official postgres image convention:
# /docker-entrypoint-initdb.d/*.sh). The image's own pg_hba.conf never adds a replication
# line, so the postgres-replica container's pg_basebackup would otherwise be refused.
# trust, not a password, because this only matters inside the isolated omyfish-net bridge
# network — no port is published for replication traffic.
set -e
echo "host replication all all trust" >> "$PGDATA/pg_hba.conf"
