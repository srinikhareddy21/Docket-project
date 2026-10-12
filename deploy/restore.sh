#!/bin/sh
# Restores a database dump and an uploads archive from the SAME backup run. DESTRUCTIVE: replaces current data.
# Usage:  sh deploy/restore.sh backups/db-<stamp>.sql.gz backups/uploads-<stamp>.tgz
set -eu
[ $# -eq 2 ] || { echo "Usage: sh deploy/restore.sh <db-dump.sql.gz> <uploads.tgz>"; exit 1; }
DB="$1"; UP="$2"
[ -f "$DB" ] && [ -f "$UP" ] || { echo "Both files must exist."; exit 1; }
COMPOSE="docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.production"
printf "This REPLACES the current database and uploads with the backup. Type RESTORE to continue: "
read -r answer
[ "$answer" = "RESTORE" ] || { echo "Cancelled."; exit 1; }

$COMPOSE stop backend web
$COMPOSE up -d mysql
echo "Waiting for MySQL..."; sleep 15
gunzip -c "$DB" | $COMPOSE exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'
docker run --rm -v docket_docket-uploads:/data -v "$(cd "$(dirname "$UP")" && pwd)":/in:ro alpine sh -c 'rm -rf /data/* && tar xzf "/in/'"$(basename "$UP")"'" -C /data'
$COMPOSE up -d
echo "Restored. Check: curl -fsS https://\$DOMAIN/api/health"
