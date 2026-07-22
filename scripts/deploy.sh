#!/usr/bin/env bash
# ============================================================================
# deploy.sh
#
# Deploy seguro da Lambda: aplica as migrations no Neon ANTES de empacotar e
# subir, garantindo que o banco já esteja atualizado. Assim o restore do
# SnapStart nunca depende da janela de ~10s (ver scripts/db-migrate.sh).
#
# Uso:
#   ./scripts/deploy.sh [prod|dev] [--yes]
#
# Exemplos:
#   ./scripts/deploy.sh              # deploy prod (confirma o changeset)
#   ./scripts/deploy.sh prod --yes   # deploy prod sem confirmação interativa
#   ./scripts/deploy.sh dev          # deploy dev
#
# Requisitos: aws cli autenticado, sam cli, ./mvnw, python3.
# ============================================================================

set -euo pipefail

cd "$(dirname "$0")/.."

ENV="prod"
NO_CONFIRM=""

for arg in "$@"; do
  case "$arg" in
    prod|dev) ENV="$arg" ;;
    --yes|-y) NO_CONFIRM="--no-confirm-changeset" ;;
    *) echo "Argumento inválido: '$arg'. Use: [prod|dev] [--yes]"; exit 1 ;;
  esac
done

SAM_CONFIG_ARGS=()
if [[ "$ENV" == "dev" ]]; then
  SAM_CONFIG_ARGS=(--config-env dev)
fi

echo "########################################################################"
echo "# 1/3  Aplicando migrations no Neon (env=$ENV) antes do deploy"
echo "########################################################################"
./scripts/db-migrate.sh migrate "$ENV"

echo ""
echo "########################################################################"
echo "# 2/3  Empacotando (mvn package -DskipTests)"
echo "########################################################################"
./mvnw -q clean package -DskipTests

echo ""
echo "########################################################################"
echo "# 3/3  sam deploy (env=$ENV)"
echo "########################################################################"
sam deploy -t target/sam.jvm.yaml "${SAM_CONFIG_ARGS[@]}" $NO_CONFIRM --no-fail-on-empty-changeset

echo ""
echo "✅ Deploy concluído (env=$ENV). Banco migrado + Lambda atualizada."
