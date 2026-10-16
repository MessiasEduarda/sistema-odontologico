#!/bin/bash
# Bootstrap do banco: cria os papéis e o schema da aplicação.
# Executado automaticamente pelo container oficial do PostgreSQL na primeira inicialização
# (e pelos testes com Testcontainers). Em produção, execute o equivalente uma única vez.
#
# Papéis:
#   MIGRATOR_DB_USER -> dono do schema; usado apenas pelo Flyway (DDL).
#   APP_DB_USER      -> usado pela aplicação; sem DDL, sem BYPASSRLS, não é dono das tabelas.
set -euo pipefail

: "${APP_DB_USER:?APP_DB_USER não definido}"
: "${APP_DB_PASSWORD:?APP_DB_PASSWORD não definido}"
: "${MIGRATOR_DB_USER:?MIGRATOR_DB_USER não definido}"
: "${MIGRATOR_DB_PASSWORD:?MIGRATOR_DB_PASSWORD não definido}"
APP_DB_SCHEMA="${APP_DB_SCHEMA:-odonto}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v db="$POSTGRES_DB" \
  -v schema="$APP_DB_SCHEMA" \
  -v app_user="$APP_DB_USER" -v app_password="$APP_DB_PASSWORD" \
  -v migrator_user="$MIGRATOR_DB_USER" -v migrator_password="$MIGRATOR_DB_PASSWORD" <<'EOSQL'
CREATE ROLE :"migrator_user" LOGIN PASSWORD :'migrator_password'
  NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_password'
  NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;

REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE :"db" TO :"app_user";
GRANT CONNECT, TEMPORARY, CREATE ON DATABASE :"db" TO :"migrator_user";

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA :"schema" AUTHORIZATION :"migrator_user";
GRANT USAGE ON SCHEMA :"schema" TO :"app_user";

-- Extensões usadas pelo modelo de dados (instaladas pelo superusuário).
CREATE EXTENSION IF NOT EXISTS citext WITH SCHEMA :"schema";
CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA :"schema";

-- Tudo que o Flyway criar fica acessível à aplicação apenas para DML.
ALTER DEFAULT PRIVILEGES FOR ROLE :"migrator_user" IN SCHEMA :"schema"
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app_user";
ALTER DEFAULT PRIVILEGES FOR ROLE :"migrator_user" IN SCHEMA :"schema"
  GRANT USAGE, SELECT ON SEQUENCES TO :"app_user";
ALTER DEFAULT PRIVILEGES FOR ROLE :"migrator_user" IN SCHEMA :"schema"
  GRANT EXECUTE ON FUNCTIONS TO :"app_user";

ALTER ROLE :"app_user" SET search_path = :"schema";
ALTER ROLE :"migrator_user" SET search_path = :"schema";
EOSQL
