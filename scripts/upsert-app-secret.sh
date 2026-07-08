#!/usr/bin/env bash
# ============================================================================
# upsert-app-secret.sh
#
# Cria ou atualiza o secret JSON no AWS Secrets Manager usado pelo deploy SAM.
# O template sam.jvm.yaml lê as chaves via dynamic reference do CloudFormation.
#
# Uso:
#   ./scripts/upsert-app-secret.sh [NOME_DO_SECRET] [ARQUIVO_ENV]
#
# Exemplos:
#   ./scripts/upsert-app-secret.sh baggagi/back/prod .env
#   ./scripts/upsert-app-secret.sh baggagi/back/dev  .env
#
# Chaves esperadas no JSON (preencha no .env ou exporte no shell):
#   QUARKUS_DATASOURCE_PASSWORD
#   R2_BUCKET_NAME, R2_ENDPOINT, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY
#   R2_PUBLIC_URL_PREFIX (opcional)
#   STRIPE_API_KEY, STRIPE_WEBHOOK_SECRET
#   STRIPE_SUCCESS_URL, STRIPE_CANCEL_URL (opcional)
#   STRIPE_PRICE_MENSAL, STRIPE_PRICE_ANUAL
#   STRIPE_PRICE_MENSAL_AGENT, STRIPE_PRICE_ANUAL_AGENT
# ============================================================================

set -euo pipefail

SECRET_NAME="${1:-baggagi/back/prod}"
ENV_FILE="${2:-.env}"
REGION="${AWS_REGION:-sa-east-1}"

KEYS=(
  QUARKUS_DATASOURCE_PASSWORD
  R2_BUCKET_NAME
  R2_ENDPOINT
  R2_ACCESS_KEY_ID
  R2_SECRET_ACCESS_KEY
  R2_PUBLIC_URL_PREFIX
  STRIPE_API_KEY
  STRIPE_WEBHOOK_SECRET
  STRIPE_SUCCESS_URL
  STRIPE_CANCEL_URL
  STRIPE_PRICE_MENSAL
  STRIPE_PRICE_ANUAL
  STRIPE_PRICE_MENSAL_AGENT
  STRIPE_PRICE_ANUAL_AGENT
)

if [[ -f "$ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  set -a
  source "$ENV_FILE"
  set +a
fi

# Defaults para URLs de redirect (podem ser sobrescritas no .env)
STRIPE_SUCCESS_URL="${STRIPE_SUCCESS_URL:-https://baggagi.com/payment/success}"
STRIPE_CANCEL_URL="${STRIPE_CANCEL_URL:-https://baggagi.com/payment/cancel}"
R2_PUBLIC_URL_PREFIX="${R2_PUBLIC_URL_PREFIX:-}"

json_payload=$(python3 - <<'PY'
import json, os, sys

keys = [
    "QUARKUS_DATASOURCE_PASSWORD",
    "R2_BUCKET_NAME", "R2_ENDPOINT", "R2_ACCESS_KEY_ID", "R2_SECRET_ACCESS_KEY",
    "R2_PUBLIC_URL_PREFIX",
    "STRIPE_API_KEY", "STRIPE_WEBHOOK_SECRET",
    "STRIPE_SUCCESS_URL", "STRIPE_CANCEL_URL",
    "STRIPE_PRICE_MENSAL", "STRIPE_PRICE_ANUAL",
    "STRIPE_PRICE_MENSAL_AGENT", "STRIPE_PRICE_ANUAL_AGENT",
]
data = {k: os.environ.get(k, "") for k in keys}
missing = [k for k in keys if not data.get(k) and k not in ("R2_PUBLIC_URL_PREFIX",)]
if missing:
    print("Chaves vazias (preencha no .env): " + ", ".join(missing), file=sys.stderr)
print(json.dumps(data))
PY
)

echo "Secret: $SECRET_NAME (region: $REGION)"
echo ""

if aws secretsmanager describe-secret --secret-id "$SECRET_NAME" --region "$REGION" >/dev/null 2>&1; then
  echo "Atualizando secret existente..."
  aws secretsmanager put-secret-value \
    --secret-id "$SECRET_NAME" \
    --region "$REGION" \
    --secret-string "$json_payload"
else
  echo "Criando novo secret..."
  aws secretsmanager create-secret \
    --name "$SECRET_NAME" \
    --region "$REGION" \
    --description "Credenciais da API Baggagi (DB, R2, Stripe)" \
    --secret-string "$json_payload"
fi

echo ""
echo "Pronto. Rode o deploy para aplicar na Lambda:"
echo "  mvn package -DskipTests && sam deploy -t target/sam.jvm.yaml"
echo ""
echo "O usuario/role do deploy precisa de secretsmanager:GetSecretValue nesse secret."
