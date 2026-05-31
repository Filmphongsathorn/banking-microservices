#!/bin/bash
set -euo pipefail

ADMIN_USER="${POSTGRES_USER:-banking_admin}"

create_database_if_missing() {
    local db_name="$1"

    if psql -v ON_ERROR_STOP=1 --username "$ADMIN_USER" --dbname postgres \
        -tAc "SELECT 1 FROM pg_database WHERE datname = '${db_name}'" | grep -q 1; then
        echo "Database ${db_name} already exists."
    else
        echo "Creating database ${db_name}..."
        createdb --username "$ADMIN_USER" --owner "$ADMIN_USER" "$db_name"
    fi
}

echo "Creating banking service databases if needed..."

create_database_if_missing "auth_db"
create_database_if_missing "profile_db"
create_database_if_missing "account_db"
create_database_if_missing "transaction_db"
create_database_if_missing "notification_db"

echo "Database initialization complete."
