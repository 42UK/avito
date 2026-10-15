#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

if [ "$#" -gt 1 ]; then
	echo "Usage: $0 [backup-name.dump]" >&2
	exit 2
fi

backup_name=${1:-"avito-deals-$(date +%Y%m%d-%H%M%S).dump"}
case "$backup_name" in
	""|*[!A-Za-z0-9._-]*|*.|..)
		echo "Backup name may contain only letters, digits, '.', '_' and '-'; it must not be '.' or '..'." >&2
		exit 2
		;;
esac
case "$backup_name" in
	*.dump) ;;
	*)
		echo "Backup name must end in .dump." >&2
		exit 2
		;;
esac

mkdir -p backups
backup_file="backups/$backup_name"
if [ -e "$backup_file" ]; then
	echo "Refusing to overwrite existing backup: $backup_file" >&2
	exit 1
fi

umask 077
if ! docker compose exec -T postgres sh -ec \
	'pg_dump --format=custom --no-owner --no-acl --username "$POSTGRES_USER" --dbname "$POSTGRES_DB"' \
	> "$backup_file"; then
	rm -f "$backup_file"
	echo "Database backup failed; incomplete output was removed." >&2
	exit 1
fi

echo "Created $backup_file (permissions restricted by umask 077). Store it securely outside Git."
