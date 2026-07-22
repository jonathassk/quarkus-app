# Contrato — Lambda de Planejamento IA (Gemini 3.5 Flash)

> Fonte da verdade para o repositório da Lambda Function URL (`NEXT_PUBLIC_PLAN_LAMBDA_STREAM_URL`).
> Este backend Quarkus **não** hospeda a Lambda; o front Next.js consome o stream diretamente.

## Modelo oficial

| Plano do usuário | Modelo |
|------------------|--------|
| Free / anônimo | **Google Gemini 3.5 Flash** (default) |
| Premium / B2B (futuro Fase 4) | Gemini 3.5 Pro ou OpenAI o4-mini |

**Motivo Flash:** grounding nativo Google Places/Maps, structured outputs, menor custo/1M tokens, streaming rápido.

## Actions existentes

1. `generatePlan` / `generateAnonymousPlan` — stream HTTP (SSE/NDJSON/JSON chunks)
2. `collectLocationInfo` — enriquecimento do destino para a espera útil

## Prompt de geração (obrigatório)

O JSON estruturado de cada atividade/refeição **deve** preencher:

- `address` (endereço completo)
- `startTime` / `endTime` (horários)
- dias de abertura / funcionamento quando aplicável (em `notes` ou campo dedicado)
- dados de contato (`site`, telefone se disponível via Places grounding)

Rejeitar ou re-promptar se horários/endereço vierem vazios em POIs principais.

## `collectLocationInfo` — schema enriquecido (espera útil)

Payload de resposta por cidade/país deve incluir abas do workspace `/create`:

```json
{
  "cities": [
    {
      "cityId": "paris",
      "overview": { "summary": "...", "bestSeason": "...", "highlights": ["..."] },
      "logistics": { "airport": "...", "transit": "...", "visa": "..." },
      "currency": {
        "code": "EUR",
        "symbol": "€",
        "approxMealCost": { "budget": 15, "mid": 35, "luxury": 80 },
        "tippingRules": "..."
      },
      "survival": {
        "etiquette": ["..."],
        "commonScams": ["..."],
        "emergencyNumbers": ["112"]
      },
      "dictionary": [
        { "phrase": "Hello", "local": "Bonjour", "pronunciation": "bon-ZHOOR" }
      ]
    }
  ]
}
```

Mínimo: **5 expressões** no dicionário; tips/golpes não vazios.

## Streaming (não mudar arquitetura)

- Manter HTTP streaming client → Function URL (sem SQS/job assíncrono)
- Timeout Lambda ≤ 30s (Valter)
- Chunks devem permitir feed vivo no front (ex.: `"activity_added": {"day":1,"name":"..."}`)

## Checklist de implementação na Lambda

- [ ] Trocar/configurar provider default → Gemini 3.5 Flash + Places grounding
- [ ] Structured output schema alinhado ao `TravelPlan` do front
- [ ] Enriquecer `collectLocationInfo` com currency/survival/dictionary
- [ ] Emitir eventos de progresso no stream para o feed da coluna esquerda
- [ ] Variáveis de ambiente: `GEMINI_API_KEY`, model id documentado no README da Lambda
