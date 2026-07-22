# Amazon SES + email-worker (Go)

E-mails automáticos **não** rodam no Quarkus Lambda. Ficam em `services/email-worker`.

O Quarkus só expõe preferências: `GET/PATCH /api/v1/users/me/email-preferences`.

## 1. Domínio no SES

1. SES → Identities → Domain (ex. `baggagi.com`) + Easy DKIM.
2. Publique CNAME/SPF/DMARC no DNS.
3. Status **Verified**.
4. Pedir **production access** (sair do sandbox) para e-mails reais.

From = o que você definir em `SES_FROM_EMAIL` (ex. `noreply@baggagi.com`), não um e-mail genérico da AWS.

## 2. Secrets Manager

No secret `baggagi/back/prod` (ou o que o SAM usar):

| Chave | Exemplo |
|-------|---------|
| `DATABASE_URL` | `postgresql://…neon.tech/neondb?sslmode=require` |
| `SES_ENABLED` | `true` |
| `SES_FROM_EMAIL` | `noreply@baggagi.com` |
| `SES_FROM_NAME` | `Baggagi` |
| `AWS_SES_REGION` | `us-east-1` |
| `APP_PUBLIC_URL` | `https://baggagi.com` |

## 3. Migração DB

```bash
./scripts/db-migrate.sh
```

Aplica `V2__email_notifications.sql` (`user_email_preferences` + `email_notification_log`).

## 4. Deploy do worker

```bash
cd services/email-worker
go mod tidy
sam build
sam deploy --guided
```

O template agenda **2 schedules** (UTC 00:00 e 12:00) com:

```json
{"action":"trip_reminders"}
```

## 5. Lembrete (&lt; 12h)

- Início da viagem = `start_date` 00:00 no TZ do user (futuro: horário do voo).
- Envia se `now` ∈ `[início − 12h, início)`.
- 2 execuções/dia garantem cobertura da janela.
- 1 e-mail por user/trip (`TRIP_REMINDER_12H`).

## 6. Atualização de produto

```bash
aws lambda invoke --function-name baggagi-email-worker \
  --cli-binary-format raw-in-base64-out \
  --payload '{"action":"product_update","subject":"Novidade","textBody":"…","dryRun":true}' \
  /tmp/out.json && cat /tmp/out.json
```

Respeita `emailUpdates` (default true se sem row).

## 7. Preferências no front

Sincronizar toggles Settings → `/api/v1/users/me/email-preferences` (Quarkus).
