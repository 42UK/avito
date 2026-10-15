#!/bin/sh
set -eu

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
	echo "Usage: $0 backup.dump [new-database-name]" >&2
	exit 2
fi

backup_file=$1
case "$backup_file" in
	/*) ;;
	*) backup_file="$PWD/$backup_file" ;;
esac
if [ ! -f "$backup_file" ]; then
	echo "Backup file not found: $backup_file" >&2
	exit 1
fi

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"
restore_db=${2:-"avito_deals_restore_$$"}
case "$restore_db" in
	[A-Za-z_]*)
		case "$restore_db" in
			*[!A-Za-z0-9_]*)
				echo "Database name may contain only ASCII letters, digits and underscores." >&2
				exit 2
				;;
		esac
		;;
	*)
		echo "Database name must start with an ASCII letter or underscore." >&2
		exit 2
		;;
esac
if [ "${#restore_db}" -gt 63 ]; then
	echo "Database name must be at most 63 characters." >&2
	exit 2
fi

if ! docker compose exec -T postgres sh -ec \
	'test "$1" != "$POSTGRES_DB"' restore-check "$restore_db"; then
	echo "Refusing to restore over the configured application database." >&2
	exit 1
fi

docker compose exec -T postgres sh -ec \
	'createdb --username "$POSTGRES_USER" "$1"' restore-create "$restore_db"
if ! docker compose exec -T postgres sh -ec \
	'pg_restore --exit-on-error --no-owner --no-privileges --username "$POSTGRES_USER" --dbname "$1"' \
	restore-import "$restore_db" < "$backup_file"; then
	echo "Restore failed. The new database '$restore_db' may be incomplete; the configured application database was not overwritten." >&2
	exit 1
fi

echo "Restored $backup_file into the new database '$restore_db'. The running backend still uses the configured application database."
