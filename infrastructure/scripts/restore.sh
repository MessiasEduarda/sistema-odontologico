#!/usr/bin/env sh
# Restaura um backup gerado por backup.sh. APAGA os dados atuais do schema e coloca os do backup no lugar.
# Uso: sh infrastructure/scripts/restore.sh backups/AAAA-MM-DD_HHMM
# Pare a API antes (para ninguém gravar durante a restauração).
set -eu

SRC="${1:?informe a pasta do backup, ex.: backups/2026-10-15_1800}"
[ -f "$SRC/banco.dump" ] || { echo "Arquivo $SRC/banco.dump não encontrado."; exit 1; }
[ -f .env ] && set -a && . ./.env && set +a
DB="${POSTGRES_DB:-odonto}"
ADMIN="${POSTGRES_ADMIN_USER:-odonto_admin}"
SCHEMA="${APP_DB_SCHEMA:-odonto}"
UPLOADS="${STORAGE_PATH:-backend/data/uploads}"

printf "Isto vai SUBSTITUIR todos os dados do banco '%s' pelos de %s. Digite RESTAURAR para continuar: " "$DB" "$SRC"
read -r answer
[ "$answer" = "RESTAURAR" ] || { echo "Cancelado."; exit 1; }

echo "Restaurando banco..."
docker compose exec -T postgres pg_restore -U "$ADMIN" -d "$DB" --clean --if-exists --no-owner \
  --schema="$SCHEMA" --schema=public < "$SRC/banco.dump"
# Reaplica as permissões dos papéis (o dump é feito sem dono).
docker compose exec -T postgres psql -U "$ADMIN" -d "$DB" -v ON_ERROR_STOP=1 -c \
  "GRANT USAGE ON SCHEMA $SCHEMA TO ${APP_DB_USER:-odonto_app};
   GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA $SCHEMA TO ${APP_DB_USER:-odonto_app};
   GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA $SCHEMA TO ${APP_DB_USER:-odonto_app};"

if [ -f "$SRC/anexos.tar.gz" ]; then
  echo "Restaurando anexos em $UPLOADS..."
  mkdir -p "$UPLOADS"
  tar -xzf "$SRC/anexos.tar.gz" -C "$UPLOADS"
fi

echo "Restauração concluída. Inicie a API: as migrações pendentes (se houver) rodam sozinhas."
