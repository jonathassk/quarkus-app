#!/usr/bin/env bash
# ============================================================================
# set-dev-env-vars.sh
#
# Configura as variáveis de ambiente da Lambda DEV (baggagi-back-dev) para usar
# a instância Neon Auth de desenvolvimento (ep-frosty-sky-aiim45vq).
#
# Uso: ./scripts/set-dev-env-vars.sh [NOME_DA_LAMBDA]
# Exemplo: ./scripts/set-dev-env-vars.sh baggagi-dev-QuarkusFunction-XXXX
# ============================================================================

set -euo pipefail

LAMBDA_FUNCTION="${1:-}"
REGION="sa-east-1"

if [[ -z "$LAMBDA_FUNCTION" ]]; then
  echo "Uso: $0 <nome-da-lambda-dev>"
  echo ""
  echo "Para descobrir o nome da função DEV:"
  echo "  aws lambda list-functions --region $REGION --query 'Functions[?contains(FunctionName, \`dev\`)].FunctionName' --output text"
  exit 1
fi

DEV_NEON_BASE_URL="https://ep-frosty-sky-aiim45vq.neonauth.c-4.us-east-1.aws.neon.tech/neondb/auth"
DEV_NEON_JWKS_URL="https://ep-frosty-sky-aiim45vq.neonauth.c-4.us-east-1.aws.neon.tech/neondb/auth/.well-known/jwks.json"
DEV_NEON_ISSUER="https://ep-frosty-sky-aiim45vq.neonauth.c-4.us-east-1.aws.neon.tech/neondb/auth"

echo "🔧 Configurando env vars Neon Auth DEV na Lambda: $LAMBDA_FUNCTION"
echo "   NEON_AUTH_BASE_URL  = $DEV_NEON_BASE_URL"
echo "   NEON_AUTH_JWKS_URL  = $DEV_NEON_JWKS_URL"
echo "   NEON_AUTH_ISSUER    = $DEV_NEON_ISSUER"
echo "   NEON_AUTH_JWK_JSON  = (vazio — busca dinâmica pelo JWKS_URL)"
echo ""

# Busca as env vars atuais da Lambda para não perder as outras
CURRENT_VARS=$(aws lambda get-function-configuration \
  --function-name "$LAMBDA_FUNCTION" \
  --region "$REGION" \
  --query 'Environment.Variables' \
  --output json 2>/dev/null || echo "{}")

# Merge: substitui/adiciona as variáveis Neon Auth
UPDATED_VARS=$(echo "$CURRENT_VARS" | python3 -c "
import json, sys
current = json.load(sys.stdin)
current['NEON_AUTH_BASE_URL']  = '$DEV_NEON_BASE_URL'
current['NEON_AUTH_JWKS_URL']  = '$DEV_NEON_JWKS_URL'
current['NEON_AUTH_ISSUER']    = '$DEV_NEON_ISSUER'
current['NEON_AUTH_JWK_JSON']  = ''
print(json.dumps({'Variables': current}))
")

aws lambda update-function-configuration \
  --function-name "$LAMBDA_FUNCTION" \
  --region "$REGION" \
  --environment "$UPDATED_VARS"

echo "✅ Variáveis atualizadas com sucesso!"
echo ""
echo "Para verificar, acesse:"
echo "  https://<API_GATEWAY_DEV_URL>/api/v1/auth/neon-status"
