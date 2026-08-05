# Runbook — retenção AWS / Cloudflare / Neon

Checklist operacional para alinhar a infraestrutura aos prazos de [`RETENCAO_DADOS.md`](./RETENCAO_DADOS.md).  
A política pública fica por sua conta; aqui só **nuvem**.

Região padrão AWS: **`sa-east-1`**.

---

## Valores-alvo (infra)

| Recurso | Alvo | Motivo |
|---|---|---|
| CloudWatch Logs (Lambdas / API) | **90 dias** | Logs operacionais comuns |
| Logs de acesso obrigatórios (se separados) | **180 dias** (6 meses) | Registros obrigatórios de acesso |
| DynamoDB `travel-location-info` (cache) | **~182 dias** TTL | Cache operacional; valor refletido na política de privacidade |
| DynamoDB `docs_needed_countries` (cache) | **~182 dias** TTL | Cache operacional; valor refletido na política de privacidade |
| DynamoDB PITR / on-demand backups | **≤ 90 dias** (ou desligado se não usar) | Backup de dados de produto |
| Neon point-in-time restore / history | **≤ 90 dias** | Backup Postgres |
| Cloudflare R2 — Incomplete Multipart | **7 dias** | Abort uploads órfãos |
| Cloudflare R2 — versões não atuais (se versioning) | **90 dias** | Alinha ao arquivo restrito |
| R2 — objetos de documentos deletados | Remoção **imediata** via API (já existe) | Sistema ativo ≤ 7 dias é teto de grace; preferir delete na hora |
| R2 — logs de visualização de documentos (`audit/document-views/…`) | Purge quando `retain_until` &lt; hoje (fim da viagem + 3 meses) | Acesso raro; storage barato |

---

## 1. AWS CloudWatch Logs — 90 dias

Hoje as Lambdas usam log groups padrão **sem** `RetentionInDays` (ficam “Never expire”).

### 1.1 Comandos (aplicar agora, sem redeploy)

Liste e ajuste (conta `252186483726`, região `sa-east-1`):

```bash
export AWS_REGION=sa-east-1
export AWS_PROFILE=default   # se usar outro perfil, troque

# Lista log groups das Lambdas Baggagi / travel-api
aws logs describe-log-groups --region "$AWS_REGION" \
  --query 'logGroups[?contains(logGroupName, `baggagi`) || contains(logGroupName, `travel`) || contains(logGroupName, `Quarkus`) || contains(logGroupName, `email-worker`)].[logGroupName,retentionInDays]' \
  --output table

# Aplique 90 dias em cada grupo encontrado (exemplo — ajuste os nomes):
for g in \
  /aws/lambda/baggagi-back-QuarkusApp-PLACEHOLDER \
  /aws/lambda/baggagi-email-worker \
  /aws/lambda/travel-api-ai-PLACEHOLDER
do
  aws logs put-retention-policy \
    --region "$AWS_REGION" \
    --log-group-name "$g" \
    --retention-in-days 90
done
```

Script mais seguro (aplica 90d a todos os grupos que casarem):

```bash
aws logs describe-log-groups --region sa-east-1 --output json \
| jq -r '.logGroups[].logGroupName' \
| grep -E 'baggagi|email-worker|travel-api|QuarkusApp|TravelApi' \
| while read -r g; do
    echo "→ $g"
    aws logs put-retention-policy --region sa-east-1 \
      --log-group-name "$g" --retention-in-days 90
  done
```

### 1.2 IaC

Log groups das Lambdas **já existem** (criados automaticamente). Não adicionar `AWS::Logs::LogGroup` nos templates SAM atuais — o create falha com *AlreadyExists*. Use só o CLI acima.

### 1.3 Registros obrigatórios de acesso (6 meses)

Se no futuro houver um log group **só** de autenticação/auditoria (ex.: `/baggagi/access-audit`), use **180** dias:

```bash
aws logs put-retention-policy --region sa-east-1 \
  --log-group-name /baggagi/access-audit \
  --retention-in-days 180
```

Até existir grupo separado, os 90 dias dos logs de Lambda cobrem o operacional comum.

---

## 2. AWS DynamoDB — TTL dos caches

### 2.1 Código (fonte dos dias)

| Tabela | Atributo TTL | Valor no código |
|---|---|---|
| `travel-location-info` (stack SAM) | `expires_at` | **~182 dias** (`locationInfoTTL`) |
| `docs_needed_countries` (pré-existente) | `expires_at` | **~182 dias** (`travelDocsTTL`) |

Arquivos: `travel-api-ai-go/internal/location_info.go`, `travel_docs.go`.

```bash
# Conferir se TTL está habilitado
aws dynamodb describe-time-to-live --region sa-east-1 \
  --table-name travel-location-info
aws dynamodb describe-time-to-live --region sa-east-1 \
  --table-name docs_needed_countries
```

Se `docs_needed_countries` estiver **Disabled**:

```bash
aws dynamodb update-time-to-live --region sa-east-1 \
  --table-name docs_needed_countries \
  --time-to-live-specification "Enabled=true,AttributeName=expires_at"
```

> **Sem backup intencional** dessas tabelas de cache: não habilitar PITR nelas (ou deixar desligado). Coordenadas de consulta não devem ir para arquivo de longo prazo.

### 2.2 PITR / backups de tabelas de produto

Para `posts_network` (e outras tabelas de produto, se houver):

```bash
aws dynamodb describe-continuous-backups --region sa-east-1 \
  --table-name posts_network

# Se PITR estiver ON e o plano permitir janela custom — documentar ≤ 90 dias.
# On-demand backups manuais: apagar backups com > 90 dias.
aws dynamodb list-backups --region sa-east-1 \
  --table-name posts_network --output table
```

---

## 3. Neon PostgreSQL — history ≤ 90 dias

Neon gerencia o restore window conforme o **plano**.

1. Abra [console.neon.tech](https://console.neon.tech) → projeto Baggagi (prod).
2. **Settings** / **Storage** / **History retention** (nome varia por plano).
3. Defina retenção de histórico / PITR em **no máximo 90 dias** (ou o máximo do plano se for menor).
4. Anote o valor neste doc (tabela abaixo).

| Ambiente | History retention configurada | Data |
|---|---|---|
| prod | _preencher_ | |
| dev | _preencher_ | |

Snapshots manuais extras: apagar após **90 dias** ou assim que o migrate/deploy estiver validado.

---

## 4. Cloudflare R2 — lifecycle

Bucket usado pela API (`R2_BUCKET_NAME` no secret `baggagi/back/prod`).

### 4.1 No dashboard

1. [Cloudflare Dashboard](https://dash.cloudflare.com) → **R2** → bucket do Baggagi.
2. **Settings** → **Object lifecycle rules** (ou equivalente).
3. Crie:

| Regra | Ação | Prazo |
|---|---|---|
| Abort incomplete multipart | Abort | **7 dias** |
| (Só se Object Versioning estiver ON) Expire noncurrent versions | Delete noncurrent | **90 dias** |

### 4.2 Via Wrangler (se preferir CLI)

```bash
# Exige wrangler autenticado na conta Cloudflare
npx wrangler r2 bucket info NOME_DO_BUCKET
```

Regras de lifecycle R2 via API/dashboard — confirme no painel; a API S3-compatible do R2 aceita lifecycle XML em contas com o recurso habilitado:

```bash
# Exemplo de intenção (ajuste bucket/credenciais R2)
# AbortIncompleteMultipartUpload = 7 dias
# NoncurrentVersionExpiration = 90 dias (somente com versioning)
```

### 4.3 Documentos de viagem

- Delete na API já remove o objeto no R2 **na hora** → ok para “sistema ativo ≤ 7 dias”.
- Lifecycle acima cobre **órfãos** (multipart falho) e **versões** residuais, não substitui o delete da app.
- Novos uploads vão **criptografados (AES-256-GCM)** para o R2; visualização só via API autenticada.

### 4.3b Logs de visualização (`audit/document-views/…`)

Prefixo: `audit/document-views/exp/{yyyy}/{MM}/{dd}/{uuid}.json`  
Retenção: **fim da viagem + 3 meses** (data no path = `retain_until`).

Purge (EventBridge diário/semanal → curl):

```bash
curl -X POST "$API_BASE/api/v1/internal/retention/purge-document-view-audits" \
  -H "X-Internal-Secret: $INTERNAL_SECRET"
```

Não use lifecycle “X dias após upload” — a expiração depende do fim da viagem.

### 4.4 Sem “backup intencional” de coordenadas no R2

Não espelhar caches de location/docs DynamoDB para R2/S3.

---

## 5. API Gateway / acesso

Se habilitar **access logging** do HTTP API / REST API:

| Destino | Retention |
|---|---|
| Log group dedicado `/baggagi/api-access` | **180 dias** |

```bash
aws logs put-retention-policy --region sa-east-1 \
  --log-group-name /baggagi/api-access \
  --retention-in-days 180
```

---

## 6. Checklist de execução

- [ ] CloudWatch: 90 dias em todos os log groups das Lambdas
- [ ] DynamoDB: TTL ON em `docs_needed_countries` (`expires_at`)
- [ ] DynamoDB: confirmar TTL ~182d nos caches (já no código; TTL ON nas tabelas)
- [ ] DynamoDB: confirmar PITR/backups de produto ≤ 90 dias (ou off em caches)
- [ ] Neon: history ≤ 90 dias (prod + dev)
- [ ] R2: lifecycle abort multipart 7d
- [ ] R2: se versioning ON → noncurrent 90d
- [ ] R2: job purge `audit/document-views` (fim viagem + 3 meses)
- [ ] Lambda: `DOCUMENTS_ENCRYPTION_KEY` (openssl rand -base64 32)
- [ ] (Opcional) API access logs → 180d
- [ ] Anotar valores reais na tabela Neon / nesta seção

---

## 7. O que **não** é resolvido só com retenção na nuvem

Estes itens dependem de **jobs na aplicação** (ver `RETENCAO_DADOS.md` RET-01…RET-03, RET-07):

- Purge de conta soft-deleted ≤ 30 dias  
- Purge de posts/eventos ≤ 30 dias  
- Hold por disputa/fraude  

A nuvem limita **quanto tempo cópias e logs sobrevivem**; a exclusão lógica → física do Postgres ainda é código.

---

## 8. Histórico

| Data | Nota |
|---|---|
| 2026-08-04 | Runbook inicial; alvos alinhados à política de retenção recomendada. |
