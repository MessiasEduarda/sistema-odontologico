#!/usr/bin/env sh
# Cópia de segurança do banco (formato custom do pg_dump) + anexos do prontuário.
# Uso (na raiz do projeto, com o Docker rodando):   sh infrastructure/scripts/backup.sh
# Gera backups/AAAA-MM-DD_HHMM/{banco.dump,anexos.tar.gz}. A pasta backups/ é ignorada pelo Git.
set -eu

[ -f .env ] && set -a && . ./.env && set +a
DB="${POSTGRES_DB:-odonto}"
ADMIN="${POSTGRES_ADMIN_USER:-odonto_admin}"
UPLOADS="${STORAGE_PATH:-backend/data/uploads}"
DEST="backups/$(date +%Y-%m-%d_%H%M)"

mkdir -p "$DEST"
echo "Gerando cópia do banco '$DB'..."
docker compose exec -T postgres pg_dump -U "$ADMIN" -d "$DB" --format=custom > "$DEST/banco.dump"

if [ -d "$UPLOADS" ]; then
  echo "Copiando anexos de $UPLOADS..."
  tar -czf "$DEST/anexos.tar.gz" -C "$UPLOADS" .
else
  echo "Aviso: pasta de anexos '$UPLOADS' não encontrada (nenhum anexo copiado)."
fi

echo "Backup concluído em $DEST"
ls -l "$DEST"
