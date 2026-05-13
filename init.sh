#!/bin/sh
set -e

psql -v ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  -v db_name="$POSTGRES_DB" \
  -v expense_user="$EXPENSE_DB_USER" \
  -v expense_password="$EXPENSE_DB_PASSWORD" <<'SQL'
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE USER :"expense_user" WITH PASSWORD :'expense_password';

GRANT ALL PRIVILEGES ON DATABASE :"db_name" TO :"expense_user";
GRANT ALL ON SCHEMA public TO :"expense_user";

ALTER SCHEMA public OWNER TO :"expense_user";

ALTER DEFAULT PRIVILEGES IN SCHEMA public
GRANT ALL ON TABLES TO :"expense_user";

ALTER DEFAULT PRIVILEGES IN SCHEMA public
GRANT ALL ON SEQUENCES TO :"expense_user";
SQL
