#!/usr/bin/env bash
# ============================================================================
# upsert-app-secret.sh
#
# Cria ou atualiza o secret JSON no AWS Secrets Manager usado pelo deploy SAM.
# O template sam.jvm.yaml lê as chaves via dynamic reference do CloudFormation.
# Merge: se o secret já existe, preserva chaves não enviadas (ex. DATABASE_URL / SES_*).
#
# Uso:
#   ./scripts/upsert-app-secret.sh [NOME_DO_SECRET] [ARQUIVO_ENV]
#
# Exemplos:
#   ./scripts/upsert-app-secret.sh baggagi/back/prod .env
#   ./scripts/upsert-app-secret.sh baggagi/back/dev  .env
#
# Chaves principais (.env):
#   QUARKUS_DATASOURCE_PASSWORD, R2_*, STRIPE_*
# Opcionais (email-worker):
#   DATABASE_URL, SES_ENABLED, SES_FROM_EMAIL, SES_FROM_NAME, AWS_SES_REGION, APP_PUBLIC_URL
# ============================================================================

set -euo pipefail

SECRET_NAME="${1:-baggagi/back/prod}"
ENV_FILE="${2:-.env}"
REGION="${AWS_REGION:-sa-east-1}"

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
SES_FROM_NAME="${SES_FROM_NAME:-Baggagi}"
AWS_SES_REGION="${AWS_SES_REGION:-us-east-1}"
APP_PUBLIC_URL="${APP_PUBLIC_URL:-https://baggagi.com}"
SES_ENABLED="${SES_ENABLED:-false}"

export STRIPE_SUCCESS_URL STRIPE_CANCEL_URL R2_PUBLIC_URL_PREFIX
export SES_FROM_NAME AWS_SES_REGION APP_PUBLIC_URL SES_ENABLED
export SES_FROM_EMAIL="${SES_FROM_EMAIL:-}"
export DATABASE_URL="${DATABASE_URL:-}"

json_payload=$(
  EXISTING="{}"
  if aws secretsmanager describe-secret --secret-id "$SECRET_NAME" --region "$REGION" >/dev/null 2>&1; then
    EXISTING="$(aws secretsmanager get-secret-value \
      --secret-id "$SECRET_NAME" --region "$REGION" \
      --query SecretString --output text)"
  fi
  export EXISTING
  python3 - <<'PY'
import json, os, sys

core = [
    "QUARKUS_DATASOURCE_PASSWORD",
    "R2_BUCKET_NAME", "R2_ENDPOINT", "R2_ACCESS_KEY_ID", "R2_SECRET_ACCESS_KEY",
    "R2_PUBLIC_URL_PREFIX",
    "STRIPE_API_KEY", "STRIPE_WEBHOOK_SECRET",
    "STRIPE_SUCCESS_URL", "STRIPE_CANCEL_URL",
    "STRIPE_PRICE_MENSAL", "STRIPE_PRICE_ANUAL",
    "STRIPE_PRICE_MENSAL_AGENT", "STRIPE_PRICE_ANUAL_AGENT",
]
optional = [
    "DATABASE_URL", "SES_ENABLED", "SES_FROM_EMAIL", "SES_FROM_NAME",
    "AWS_SES_REGION", "APP_PUBLIC_URL",
]

existing = json.loads(os.environ.get("EXISTING") or "{}")
data = dict(existing)

for k in core + optional:
    v = os.environ.get(k)
    if v is not None and v != "":
        data[k] = v

missing = [k for k in core if not data.get(k) and k not in ("R2_PUBLIC_URL_PREFIX",)]
if missing:
    print("Chaves vazias (preencha no .env): " + ", ".join(missing), file=sys.stderr)
print(json.dumps(data))
PY
)

echo "Secret: $SECRET_NAME (region: $REGION)"
echo ""

if aws secretsmanager describe-secret --secret-id "$SECRET_NAME" --region "$REGION" >/dev/null 2>&1; then
  echo "Atualizando secret existente (merge)..."
  aws secretsmanager put-secret-value \
    --secret-id "$SECRET_NAME" \
    --region "$REGION" \
    --secret-string "$json_payload"
else
  echo "Criando novo secret..."
  aws secretsmanager create-secret \
    --name "$SECRET_NAME" \
    --region "$REGION" \
    --description "Credenciais da API Baggagi (DB, R2, Stripe, SES)" \
    --secret-string "$json_payload"
fi

echo ""
echo "Pronto."
echo "  API:    mvn package -DskipTests && sam deploy -t target/sam.jvm.yaml"
echo "  E-mail: cd services/email-worker && sam build && sam deploy"
echo ""
echo "O usuario/role do deploy precisa de secretsmanager:GetSecretValue nesse secret."
