# Fase 4 — Backlog Enterprise (contratos)

Itens futuros. Não implementar produção até critérios de go/no-go.

## 4.1 Co-Browsing em tempo real

**Objetivo:** agente e cliente veem o mesmo roteiro/mapa durante ligação.

**Reuse:** gateway WebSocket já usado em chat (`ChatBroadcastService` / WS token).

**Contrato proposto:**

```json
{
  "type": "CO_BROWSE_STATE",
  "conversationId": "uuid",
  "tripId": "uuid",
  "payload": {
    "view": "map|itinerary|day",
    "dayNumber": 2,
    "lat": -23.55,
    "lng": -46.63,
    "selectedActivityId": "uuid"
  }
}
```

- Papéis: presenter (agente) / follower (cliente guest)
- Rate-limit igual ao chat
- Sem persistência obrigatória (ephemeral)

## 4.2 Agente coletor de documentos (OCR WhatsApp)

**Fluxo:** bot solicita foto do passaporte → Vision OCR → upsert `document_expiry` / user profile.

**Pré-requisitos:** Meta WhatsApp Business API, fila dedicada, consentimento LGPD.

**Endpoint futuro (Quarkus):** `POST /api/v1/agency/ocr/passport` (internal worker callback).

## 4.3 Agente Guardião & Reacomodação (HITL)

Monitora status de voos; cria rascunho de reacomodação; agente humano confirma 1 clique.

**Tabelas futuras:** `flight_watches`, `reaccommodation_drafts` (`status=PENDING_HUMAN`).

## 4.4 Stripe Connect — split de comissão

Divisão automática: operadora / agência / agente autônomo.

**Go/no-go:** Connect onboarding estável + KYC BR + modelo de fee definido por Sarah.

**Não iniciar** antes dos itens acima.

## 4.5 Roteamento de modelo IA

Assinantes Premium/B2B → Gemini 3.5 Pro ou o4-mini (ver [AI_LAMBDA_GEMINI.md](AI_LAMBDA_GEMINI.md)).
