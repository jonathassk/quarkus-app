# email-worker (Go + SES)

Lambda dedicada para e-mails automáticos da Baggagi.

## Actions

| `action` | Disparo | Comportamento |
|----------|---------|---------------|
| `trip_reminders` | EventBridge **2×/dia** (00:00 e 12:00 UTC) | E-mail se faltam **&lt; 12h** para o início do dia da viagem (`start_date` 00:00 no TZ do user) |
| `document_expiry_reminders` | EventBridge / invoke | Alertas de documentos perto do vencimento |
| `product_update` | Invoke manual | Broadcast para opt-in `emailUpdates` |
| `send_direct` | Quarkus `POST /api/v1/trips/{id}/email` | E-mail pontual com resumo do roteiro (corpo já montado no front/Quarkus) |
| `send_white_label` | Invoke (proposta / D-7 / pós-venda) | HTML com logo/cor da agência (`agencyId`); From SES continua Baggagi |

Payloads:

```json
{"action":"trip_reminders"}

{
  "action": "product_update",
  "subject": "Novidade no Baggagi",
  "textBody": "Lançamos X…",
  "htmlBody": "<p>Lançamos <strong>X</strong>…</p>",
  "broadcastId": "11111111-1111-1111-1111-111111111111",
  "dryRun": false
}

{
  "action": "send_direct",
  "toEmail": "voce@email.com",
  "subject": "Roteiro: Paris",
  "textBody": "…",
  "htmlBody": "<p>…</p>"
}

{
  "action": "send_white_label",
  "toEmail": "cliente@email.com",
  "agencyId": "uuid-da-agencia",
  "templateKind": "proposal_sent",
  "tripName": "Paris 7 dias",
  "shareUrl": "https://baggagi.com/p/abc123"
}
```

`templateKind`: `proposal_sent` | `boarding_reminder` | `post_trip`.
## Preferências (Quarkus)

`GET/PATCH /api/v1/users/me/email-preferences` — `emailUpdates`, `tripReminders`.

Tabelas (Flyway V2): `user_email_preferences`, `email_notification_log`.

## Build & deploy

```bash
cd services/email-worker
go mod tidy
sam build
sam deploy --guided
```

Secrets Manager (`baggagi/back/prod`): `DATABASE_URL`, `SES_ENABLED`, `SES_FROM_EMAIL`, `SES_FROM_NAME`, `AWS_SES_REGION`, `APP_PUBLIC_URL`.

## Lembrete &lt; 12h

Hoje: início = `start_date` às **00:00** no timezone do usuário (ou UTC).

Quando existir horário de voo, trocar `tripStartAt` em `internal/jobs/reminders.go` pela datetime do voo — a janela de 12h e o cron 2×/dia permanecem.

Idempotência: tipo `TRIP_REMINDER_12H` + `(user_id, trip_id)`.

Ver também `docs/SES_SETUP.md` na raiz do quarkus-app.
