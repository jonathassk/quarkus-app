#!/usr/bin/env bash
# Cria (ou lista) os Price IDs B2B Essencial/Team no Stripe Test Mode via Stripe CLI.
# Uso: ./scripts/stripe-sync-plans.sh
set -euo pipefail

if ! command -v stripe >/dev/null 2>&1; then
  echo "Stripe CLI não encontrado. Instale: https://stripe.com/docs/stripe-cli" >&2
  exit 1
fi

echo "Price IDs atuais (teste) — cole no .env / secrets:"
cat <<'EOF'
# Premium (já existentes)
STRIPE_PRICE_MENSAL=price_1TioBIFEv5goion87hou768W
STRIPE_PRICE_ANUAL=price_1TioBRFEv5goion8w7aCmTXv
# Solo / Agente
STRIPE_PRICE_MENSAL_AGENT=price_1TioBXFEv5goion8SfpV4biV
STRIPE_PRICE_ANUAL_AGENT=price_1TioBfFEv5goion87k7gMwuo
# Essencial
STRIPE_PRICE_MENSAL_AGENT_STARTER=price_1Txd2mFEv5goion8VxoCZr7S
STRIPE_PRICE_ANUAL_AGENT_STARTER=price_1Txd2oFEv5goion8AFCbKnD6
# Team
STRIPE_PRICE_MENSAL_AGENT_TEAM=price_1Txd2rFEv5goion8BiQQXhNL
STRIPE_PRICE_ANUAL_AGENT_TEAM=price_1Txd2tFEv5goion8Q2IRIo7t
EOF

echo
echo "Para listar produtos ao vivo: stripe products list --limit 20"
