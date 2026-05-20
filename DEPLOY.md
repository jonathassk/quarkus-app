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
