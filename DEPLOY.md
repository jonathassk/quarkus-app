# Deploy da aplicação no AWS Lambda

## Pré-requisitos

- **AWS CLI** instalado e configurado (`aws configure` com suas credenciais)
- **SAM CLI** instalado: [Instalar AWS SAM CLI](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html)
- Java 21 (já usado no projeto)

## Passo a passo

### 1. Build do projeto

Na raiz do projeto:

```bash
mvn package -DskipTests
```

Isso gera em `target/`:

- `function.zip` – pacote da Lambda  
- `sam.jvm.yaml` – template SAM (já com SnapStart e parâmetro da senha do banco)

### 2. Deploy no AWS (primeira vez – modo guiado)

```bash
sam deploy -t target/sam.jvm.yaml -g
```

O `-g` (guided) pergunta:

- **Stack Name**: nome da stack (ex: `quarkus-app`)
- **AWS Region**: região (ex: `us-east-1`)
- **DbPassword**: senha do PostgreSQL (Neon) – a mesma que você usa no `.env` local
- Confirmação de alterações

A senha é passada como variável de ambiente `QUARKUS_DATASOURCE_PASSWORD` para a Lambda, então a aplicação consegue conectar no Neon.

### 3. Deploy nas próximas vezes

Depois do primeiro deploy, você pode usar um arquivo de configuração para não repetir as perguntas. O SAM pode gerar um `samconfig.toml` na primeira vez; nas próximas:

```bash
mvn package -DskipTests
sam deploy -t target/sam.jvm.yaml
```

Para trocar a senha do banco em um deploy:

```bash
sam deploy -t target/sam.jvm.yaml --parameter-overrides DbPassword="nova_senha"
```

### 4. URL da API

Após o deploy, o SAM mostra a URL da API, algo como:

```
Key                 LambdaHttpApi
Value               https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/
```

Use essa URL como base (ex: `https://.../hello` para um endpoint `/hello`).

---

## Testar localmente antes do deploy

```bash
sam local start-api --template target/sam.jvm.yaml
```

A API fica em `http://127.0.0.1:3000`. Para a Lambda usar a senha do banco, defina a variável antes:

```bash
export QUARKUS_DATASOURCE_PASSWORD="sua_senha"
sam local start-api --template target/sam.jvm.yaml
```

---

## Senha do banco sem usar parâmetro no deploy

Se preferir não passar a senha na linha de comando:

1. Faça o deploy uma vez (pode usar um valor qualquer em `DbPassword`).
2. No **Console da AWS** → **Lambda** → sua função → **Configuration** → **Environment variables**.
3. Edite e defina `QUARKUS_DATASOURCE_PASSWORD` com a senha correta do Neon.

Nos próximos deploys o template não altera variáveis que você já configurou manualmente, a menos que você use `--parameter-overrides` de novo.

---

## Conexão com Neon na Lambda (checklist)

A aplicação está configurada para usar o **Neon PostgreSQL** (endpoint pooler). Confirme:

| Item | Onde verificar |
|------|----------------|
| **Senha** | Lambda → Configuration → Environment variables → `QUARKUS_DATASOURCE_PASSWORD` = senha **atual** do Neon (usuário `neondb_owner`). Se o log mostrar `password authentication failed for user 'neondb_owner'`, a senha na Lambda está errada — atualize no console ou rode o deploy com `--parameter-overrides DbPassword="..."`. |
| **URL do banco** | Por padrão: `ep-steep-night-ai1jrfuk-pooler...neon.tech:5432/neondb`. Para mudar: defina `QUARKUS_DATASOURCE_JDBC_URL` na Lambda. |
| **Lambda sem VPC** | Lambda → Configuration → VPC = **No VPC** (obrigatório para acessar internet/Neon com o template atual). |
| **Projeto Neon ativo** | [Neon Console](https://console.neon.tech) → projeto não pode estar pausado. |

---

## Erro "UnknownHostException" ou "The connection attempt failed" na Lambda

Se a Lambda falhar ao subir com **`java.net.UnknownHostException`** (host do banco) ou **"The connection attempt failed"**, a função **não está conseguindo acessar a internet** (resolver DNS ou conectar ao Neon).

### Passo a passo para remover VPC (obrigatório para acessar Neon)

1. Abra o **Console AWS** → **Lambda** → clique na sua função (ex.: **QuarkusApp**).
2. Aba **Configuration** → no menu lateral, **VPC**.
3. Se aparecer **VPC**, **Subnets** e **Security groups** preenchidos:
   - Clique em **Edit**.
   - Em **VPC**, mude para **No VPC** (ou "No VPC" / "Nenhuma VPC").
   - **Save**.
4. Faça um novo deploy para garantir que o template está aplicado:
   ```bash
   mvn package -DskipTests
   sam deploy -t target/sam.jvm.yaml
   ```
5. Teste de novo a função. O template já define **JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true** para forçar IPv4 na resolução DNS.

### Outras causas

- **Lambda em VPC sem saída para internet:** se a função **precisar** ficar em VPC, é obrigatório ter **NAT Gateway** (subnet pública com NAT, rota `0.0.0.0/0` da subnet privada para o NAT). Sem isso, a Lambda não resolve DNS nem acessa o Neon.
- **Neon pausado:** [Neon Console](https://console.neon.tech) → projeto deve estar ativo. Se estiver pausado, reative e aguarde alguns minutos.
- **Host correto:** Neon → **Connection string** → confira se o host é o mesmo usado na aplicação (`ep-steep-night-ai1jrfuk-pooler.c-4.us-east-1.aws.neon.tech`).

---

## 401 vazio em `session-sync` / viagens / perfil (logado no Neon)

Sintoma no navegador: `POST /api/v1/auth/session-sync` → **401** com corpo vazio e `www-authenticate: Bearer`; o front fica em `hydrate:setAuth-neon-only` e recursos privados falham.

### Causa

O **API Gateway HTTP API** tem (ou teve) **JWT Authorizer** no header `Authorization`. Ele valida o token **antes** da Lambda Quarkus. Tokens **Neon Auth (EdDSA)** não passam nesse authorizer (geralmente configurado para Cognito/RS256) → **401 na borda**, sem chegar no `NeonAuthJwtVerifier`.

Confirmação:

```bash
# Com Authorization → 401 vazio (bloqueio no API Gateway)
curl -i -X POST "https://api.baggagi.com/api/v1/auth/session-sync" \
  -H "Authorization: Bearer QUALQUER_COISA"

# Sem Authorization → 401 JSON da Quarkus (chegou na Lambda)
curl -i -X POST "https://api.baggagi.com/api/v1/auth/session-sync"
```

### Correção recomendada (infra)

1. No API Gateway, **remova o JWT Authorizer** das rotas (a Quarkus já valida o JWT Neon).
2. Ou reconfigure o authorizer para o JWKS EdDSA do Neon (`{NEON_AUTH_BASE_URL}/.well-known/jwks.json`).

### Correção no código (já aplicada)

- **Front:** envia o JWT em `X-Baggagi-Authorization` (não em `Authorization`).
- **Backend:** lê `X-Baggagi-Authorization` ou `Authorization` (`RequestAuthHeaders`).
- **CORS:** incluir `X-Baggagi-Authorization` em `quarkus.http.cors.headers` e `QUARKUS_HTTP_CORS_ORIGINS` na Lambda.

Após **redeploy da Lambda** e **deploy do front**, teste:

```bash
curl -sS -X POST "https://api.baggagi.com/api/v1/auth/session-sync" \
  -H "X-Baggagi-Authorization: Bearer SEU_JWT_NEON"
```

Esperado: **200** com `id` numérico do usuário.

---

## Erro 403 no login/registro (frontend em produção)

Se o navegador mostra **`POST .../users/login` → 403** com o site em `https://baggagi.com` (ou domínio parecido), a causa mais comum é **CORS**: o backend só aceitava `http://localhost:3000` como `Origin`.

### Como confirmar

```bash
# Sem Origin → deve chegar no backend (401/400, não 403 vazio)
curl -i -X POST "https://api.baggagi.com/api/v1/users/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"a@b.com","password":"abc"}'

# Com Origin do frontend → antes do fix retornava 403
curl -i -X POST "https://api.baggagi.com/api/v1/users/login" \
  -H "Content-Type: application/json" \
  -H "Origin: https://baggagi.com" \
  -d '{"email":"a@b.com","password":"abc"}'
```

### Correção

1. Garanta que `quarkus.http.cors.origins` inclui o domínio do frontend (ex.: `https://baggagi.com`, `https://www.baggagi.com`).
2. Na Lambda, defina também `QUARKUS_HTTP_CORS_ORIGINS` com a mesma lista (o template SAM já envia isso após rebuild).
3. Faça **redeploy**:
   ```bash
   mvn package -DskipTests
   sam deploy -t target/sam.jvm.yaml
   ```
4. No frontend, use a URL correta da API (`api.baggagi.com` conforme o DNS). Se estiver em `api.baqqaqi.com` (grafia diferente), ajuste DNS ou a variável `NEXT_PUBLIC_API_URL`.

### Cloudflare (recomendado para API)

No registro **`api`** do DNS, prefira **DNS only** (nuvem cinza), não **Proxied** (laranja), ao apontar para o API Gateway. O proxy da Cloudflare em cima da API costuma gerar erros extras em POST/autenticação.

---

## Deploy falhou: `UPDATE_FAILED` / `function initialization`

Se o SAM mostrar:

```text
CREATE_FAILED AWS::Lambda::Version ... did not stabilize
Status Reason: An error occurred during function initialization.
```

a nova versão da Lambda **não conseguiu subir** (Quarkus/Hibernate/banco no init do SnapStart). A stack pode ficar em `UPDATE_FAILED`.

### 1. Voltar a stack ao estado estável (se necessário)

```bash
aws cloudformation rollback-stack --stack-name baggagi-back
```

Aguarde o status voltar para `UPDATE_ROLLBACK_COMPLETE` ou `UPDATE_COMPLETE`.

### 2. Corrigir e redeployar

O projeto usa o perfil **`lambda`** (`QUARKUS_PROFILE=lambda`):

- Hibernate **sem DDL** no cold start (`database.generation=none`)
- Mongo desabilitado na Lambda
- Timeout **30s**, memória **1024 MB**
- CORS de produção no API Gateway + Quarkus

```bash
mvn package -DskipTests
sam deploy -t target/sam.jvm.yaml --parameter-overrides DbPassword="SUA_SENHA_NEON"
```

Use a **mesma senha** do Neon. Se `DbPassword` estiver errada no deploy, o init falha de novo.

### 3. Conferir logs (se ainda falhar)

Console AWS → **CloudWatch** → log group `/aws/lambda/baggagi-back-QuarkusApp-...` → último stream após o deploy. Procure `PersistenceException`, `PSQLException` ou `UnknownHostException`.

---

## Erro `column auth_user_id does not exist`

Se o log mostrar coluna `auth_user_id` (ou legado `cognito_sub`) ausente, o banco ainda não recebeu as migrações Flyway.

### Correção rápida (Neon SQL Editor)

Execute [`scripts/neon-schema-auth.sql`](scripts/neon-schema-auth.sql) no [Neon Console](https://console.neon.tech) → **SQL Editor**.

### Correção permanente (recomendado)

```bash
mvn package -DskipTests
sam deploy -t target/sam.jvm.yaml
```

Flyway aplica `V1__...` e `V7__neon_auth_user_id.sql` na subida da Lambda.

---

## Upload de documentos (400 / storage)

### Sintoma

Front: *Could not upload document* com **400** — em geral `Unsupported content type: application/octet-stream` (navegador sem MIME) ou tipo de arquivo não permitido.

### API

- **Recomendado:** `POST /api/v1/trips/{id}/documents/upload` — `multipart/form-data` (`file`, `title` opcional). A Lambda envia ao R2; **sem CORS no browser** para `cloudflarestorage.com`.
- Legado: `upload-request` → `PUT` no R2 → `upload-confirm` (exige CORS no bucket).

Tipos aceitos: PDF, JPEG, PNG, WebP, GIF, DOC, DOCX. O backend infere MIME pela extensão quando o browser envia `octet-stream`.

### Banco

Flyway **`V2__trip_documents.sql`** cria a tabela `trip_documents`. Sem ela, o upload pode falhar com **500** (não 400).

**`duplicate key ... trip_documents_pkey` (id já existe):** a sequence `trip_documents_seq` ficou atrás do `MAX(id)` (comum após V2 com BIGSERIAL + V3). Rode no Neon e redeploy com **V4**:

```sql
SELECT setval(
    'trip_documents_seq',
    COALESCE((SELECT MAX(id) FROM trip_documents), 1),
    true
);
```

Depois disso, `upload-request` volta a gerar IDs novos (ex.: 11, 12…).

### Variáveis na Lambda (API principal)

| Variável | Descrição |
|----------|-----------|
| `R2_BUCKET_NAME` | Bucket Cloudflare R2 |
| `R2_ENDPOINT` | Ex.: `https://<account>.r2.cloudflarestorage.com` |
| `R2_ACCESS_KEY_ID` | Access key R2 |
| `R2_SECRET_ACCESS_KEY` | Secret R2 |

Se faltarem, a API responde **503** `STORAGE_NOT_CONFIGURED` (não 400).

Após alterar env vars: `mvn package -DskipTests` e `sam deploy`.

### CORS no bucket R2 (PUT do navegador)

**Sintoma:** no DevTools, `PUT` para `*.r2.cloudflarestorage.com` falha com *blocked by CORS policy* / `No 'Access-Control-Allow-Origin' header`. O `upload-request` na API pode retornar 200; o erro aparece só no passo 2 (upload direto no R2).

**Causa:** CORS da API (Quarkus/Lambda) não se aplica ao domínio do R2. É preciso política CORS **no bucket** no painel Cloudflare.

1. Cloudflare → **R2** → seu bucket → **Settings** → **CORS policy**
2. Cole (ajuste domínios se necessário):

```json
[
  {
    "AllowedOrigins": [
      "https://baggaqi.com",
      "https://www.baggaqi.com",
      "https://baqqaqi.com",
      "https://www.baqqaqi.com",
      "https://baggagi.com",
      "https://www.baggagi.com",
      "http://localhost:3000"
    ],
    "AllowedMethods": ["GET", "PUT", "HEAD"],
    "AllowedHeaders": ["*"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3600
  }
]
```

3. Salve e teste de novo o upload (hard refresh se necessário).

**URL com `%20` no nome do bucket** (ex.: `.../r2.cloudflarestorage.com/%20meu-bucket/...`): `R2_BUCKET_NAME` na Lambda provavelmente tem espaço no início/fim. Corrija para o nome exato do bucket (sem aspas extras no console AWS/Lambda). O código faz `trim` no nome ao subir.

**GET** (visualizar documento) também usa URL pré-assinada no browser; as mesmas origens e métodos `GET`/`HEAD` na política acima cobrem preview e download.

**Registro no Neon mas arquivo ausente no R2:** o `upload-request` grava `trip_documents` com status `PENDING` e devolve a URL; o `PUT` no R2 é no browser. Se o CORS falhar, o arquivo não sobe e o `upload-confirm` não roda — a lista na API só mostra `READY`. Limpe linhas `PENDING` órfãs no SQL se quiser, ou ignore até o CORS estar certo.

O `Origin` no erro do DevTools deve aparecer **literalmente** em `AllowedOrigins` (inclua `www` se o site redirecionar para ele).

---

## Neon Auth — login Google e tabela `users`

O **Neon Auth** grava identidade em `neon_auth.*` no mesmo Postgres; a API mantém o perfil da aplicação em `users` (id numérico para viagens, etc.).

| Caminho | Quando |
|--------|--------|
| **`POST /api/v1/auth/session-sync`** | Front chama após **todo** login (Google via Neon Auth) — **principal** |
| **JIT** (`UserSyncService`) | Qualquer API com JWT Neon Auth válido |

Conta Google já existente (mesmo e-mail do Cognito): o JIT **vincula por e-mail** e preenche `auth_user_id` com o UUID do Neon Auth.

### Variáveis na Lambda

| Variável | Uso |
|----------|-----|
| `NEON_AUTH_BASE_URL` | **Apenas o domínio base** do Neon Auth — sem `/neondb/auth`. Deve ser igual ao campo `iss` do JWT. O verifier constrói o JWKS como `{URL}/.well-known/jwks.json`. Ex.: `https://ep-steep-night-ai1jrfuk.neonauth.c-4.us-east-1.aws.neon.tech` |

### Conferir no Neon

```sql
SELECT id, email, full_name, auth_user_id, provider FROM users ORDER BY id DESC LIMIT 20;
SELECT id, email, name FROM neon_auth.users_sync ORDER BY created_at DESC LIMIT 10;
```

### Conferir na API

```bash
curl -s -X POST https://api.baggagi.com/api/v1/auth/session-sync \
  -H "Authorization: Bearer SEU_JWT_NEON_AUTH"
```

Resposta `200` com `id` numérico = usuário vinculado em `users`.

### Validar deploy (obrigatório após mudanças de auth)

Se `GET /api/v1/auth/neon-status` retornar **404 HTML**, a Lambda ainda está com build antigo (sem `NeonAuthJwtVerifier`).

```bash
# 1) Verificador Neon ativo (sem token)
curl -sS https://api.baggagi.com/api/v1/auth/neon-status

# 2) Com JWT do login (eyJ… do /api/auth/token, NÃO o session.token do get-session)
curl -sS https://api.baggagi.com/api/v1/auth/neon-status \
  -H "Authorization: Bearer SEU_JWT"

# 3) session-sync e listagem
curl -sS -X POST https://api.baggagi.com/api/v1/auth/session-sync \
  -H "Authorization: Bearer SEU_JWT"
curl -sS https://api.baggagi.com/api/v1/trips \
  -H "Authorization: Bearer SEU_JWT"
```

Esperado: `neonVerifierConfigured: true`, `tokenValid: true`, `session-sync` **200**, `trips` **200** (array, pode ser `[]`).

Deploy backend:

```bash
cd quarkus-app
mvn -DskipTests package
# sam deploy (ou seu pipeline) — confira QUARKUS_PROFILE=lambda e NEON_AUTH_BASE_URL na função
```

### Frontend (repositório separado)

1. Habilitar **Neon Auth** no projeto Neon e configurar **Google OAuth** (mesmo Client ID/Secret do Google Cloud, se já usava Cognito).
2. Usar o SDK Neon Auth (`authClient.signIn.social({ provider: "google" })`).
3. Obter JWT com `authClient.token()` e enviar `Authorization: Bearer` na API.
4. Chamar `session-sync` após cada login.
5. Renovar JWT antes de expirar (~15 min) com `authClient.token()`.

Documentação: [Neon Auth](https://neon.com/docs/auth/overview), [JWT](https://neon.com/docs/auth/guides/plugins/jwt), [Google OAuth](https://neon.com/docs/auth/guides/setup-oauth).
