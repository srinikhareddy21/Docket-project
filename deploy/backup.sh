#!/bin/sh
# Backs up the database AND the uploaded files together (they must be restored as a pair).
# Run from the repository root:  sh deploy/backup.sh      Suggested cron: 0 2 * * *  cd /opt/docket && sh deploy/backup.sh
set -eu
COMPOSE="docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.production"
DIR="${BACKUP_DIR:-./backups}"
KEEP_DAYS="${KEEP_DAYS:-14}"
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
mkdir -p "$DIR"
umask 077

# --single-transaction gives a consistent snapshot without locking the app.
$COMPOSE exec -T mysql sh -c 'mysqldump --single-transaction --routines -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' | gzip > "$DIR/db-$STAMP.sql.gz"
docker run --rm -v docket_docket-uploads:/data:ro -v "$(cd "$DIR" && pwd)":/out alpine tar czf "/out/uploads-$STAMP.tgz" -C /data .

# A backup that cannot be read back is not a backup.
gzip -t "$DIR/db-$STAMP.sql.gz"
tar tzf "$DIR/uploads-$STAMP.tgz" > /dev/null
echo "Backup written: $DIR/db-$STAMP.sql.gz and $DIR/uploads-$STAMP.tgz"

find "$DIR" -name 'db-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
find "$DIR" -name 'uploads-*.tgz' -mtime +"$KEEP_DAYS" -delete
echo "Copy these files off this server (another machine or object storage) to survive a disk failure."
