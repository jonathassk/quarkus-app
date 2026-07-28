#!/usr/bin/env bash
# ============================================================================
# db-migrate.sh
#
# Aplica as migrations Flyway no banco Neon FORA da Lambda.
#
# Por que existe:
#   A Lambda NÃO aplica migrations (SnapStart restore não chama Flyway — isso
#   custava ~6–7s mesmo com schema up to date). Este script é o caminho oficial
#   para alterar o schema no Neon, usando o mesmo Flyway do Quarkus
#   (flyway-core via perfil Maven -Pflyway).
#
#   Rode SEMPRE antes de deployar código que depende de migration nova
#   (./scripts/deploy.sh já faz isso; o CI também).
#
# Uso:
#   ./scripts/db-migrate.sh [migrate|info|validate|repair] [prod|dev]
#
# Exemplos:
#   ./scripts/db-migrate.sh                # migrate no prod (padrão)
#   ./scripts/db-migrate.sh info           # lista o estado das migrations (prod)
#   ./scripts/db-migrate.sh migrate dev    # migrate no ambiente dev
#   ./scripts/db-migrate.sh validate prod  # valida checksums
#
# Requisitos: aws cli autenticado, python3, ./mvnw. A senha do banco é lida do
# AWS Secrets Manager (baggagi/back/<env>) — nunca fica hardcoded.
# ============================================================================

set -euo pipefail

cd "$(dirname "$0")/.."

COMMAND="${1:-migrate}"
ENV="${2:-prod}"
REGION="sa-east-1"

case "$COMMAND" in
  migrate|info|validate|repair) ;;
  *) echo "Comando inválido: '$COMMAND'. Use: migrate | info | validate | repair"; exit 1 ;;
esac

case "$ENV" in
  prod) SECRET_ID="baggagi/back/prod" ;;
  dev)  SECRET_ID="baggagi/back/dev" ;;
  *) echo "Ambiente inválido: '$ENV'. Use: prod | dev"; exit 1 ;;
esac

# URL/usuário do Flyway: mesmos defaults de application.properties (quarkus.flyway.*).
# Endpoint DIRETO (não-pooler) do Neon, igual ao usado pela Lambda no restore.
FLYWAY_URL="${QUARKUS_FLYWAY_JDBC_URL:-jdbc:postgresql://ep-steep-night-ai1jrfuk.c-4.us-east-1.aws.neon.tech:5432/neondb?sslmode=require&channel_binding=require&connectTimeout=15000&socketTimeout=60000}"
FLYWAY_USER="${QUARKUS_FLYWAY_USERNAME:-neondb_owner}"

echo "==> Flyway '$COMMAND' | env=$ENV | secret=$SECRET_ID"
echo "    URL: $FLYWAY_URL"

echo "==> Lendo senha do banco no Secrets Manager..."
DB_PASS="$(aws secretsmanager get-secret-value \
  --secret-id "$SECRET_ID" \
  --region "$REGION" \
  --query SecretString --output text \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['QUARKUS_DATASOURCE_PASSWORD'])")"

if [[ -z "$DB_PASS" ]]; then
  echo "ERRO: não consegui obter QUARKUS_DATASOURCE_PASSWORD do secret $SECRET_ID"
  exit 1
fi
echo "    senha carregada (${#DB_PASS} caracteres)"

echo "==> Executando ./mvnw -Pflyway flyway:$COMMAND ..."
./mvnw -ntp -Pflyway "flyway:$COMMAND" \
  -Dflyway.url="$FLYWAY_URL" \
  -Dflyway.user="$FLYWAY_USER" \
  -Dflyway.password="$DB_PASS" \
  -Dflyway.baselineOnMigrate=false

echo "==> Concluído."
