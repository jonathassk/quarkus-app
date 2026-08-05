# Planejamento de retenção de dados — Baggagi

Documento operacional e de conformidade. Os prazos abaixo são a **política de retenção inicial recomendada** e devem alimentar a seção **8. Retenção** da Política de Privacidade (`front-travel/lib/legal/privacy/*`).

> **Escopo do gatilho:** salvo indicação em contrário, os prazos de “sistema ativo” e “backup” contam **a partir da exclusão lógica / solicitação do titular / fim da necessidade operacional** (ex.: exclusão de conta, exclusão de viagem, exclusão de post), e **não** enquanto a conta ou o conteúdo permanecerem ativos e em uso legítimo.

---

## 1. Princípios

1. **Minimização** — manter só o necessário à finalidade.
2. **Duas camadas** — (A) sistema ativo / banco de produção; (B) backups ou arquivo restrito.
3. **Exceção de disputa/fraude** — prevalece sobre prazos curtos enquanto o caso exigir.
4. **Obrigações legais** — cobrança e registros obrigatórios de acesso podem superar os prazos de conteúdo do produto.
5. **PT-BR prevalece** na política pública; este doc é a fonte numérica para engenharia e jurídico.

---

## 2. Tabela-mestre (valores oficiais)

| Categoria | Sistema ativo | Backups ou arquivo restrito |
|---|---|---|
| Conta e perfil | Até **30 dias** | Até **90 dias** |
| Viagens | Até **30 dias** | Até **90 dias** |
| Posts e eventos | Até **30 dias** | Até **90 dias** |
| Documentos de viagem | Até **7 dias** | Até **90 dias** |
| Coordenadas de localização (cache de consulta) | Até **~182 dias** (TTL DynamoDB) | Sem backup intencional (sem PITR) |
| Documentos de referência por país (cache `docs_needed`) | Até **~182 dias** (TTL DynamoDB) | Sem backup intencional (sem PITR) |
| Logs operacionais comuns | **90 dias** | Conforme necessidade de segurança |
| Registros obrigatórios de acesso | **6 meses** | Sob controle e sigilo |
| Dados de cobrança | Conforme obrigação legal | A definir com contador |
| Dados ligados a disputa ou fraude | Enquanto necessários ao caso | Acesso restrito |

---

## 3. Mapeamento por categoria → sistemas / entidades

### 3.1 Conta e perfil — 30 / 90 dias

**Inclui:** `users` (nome, e-mail, username, avatar, preferências), `user_privacy_settings`, `user_email_preferences`, `user_travel_preferences`, `user_follows`, autenticação/sessão vinculada à conta.

**Gatilho:** exclusão de conta solicitada pelo titular (ou exclusão por violação / menoridade).

**Fluxo proposto:**
1. Soft-delete imediato (`deleted_at`) + bloqueio de login e de visibilidade pública.
2. Purge hard no sistema ativo em até **30 dias**.
3. Remanescentes em backup eliminados em até **90 dias** do soft-delete (rotação natural do backup).

**Observação:** exclusão de conta ainda está em backlog (`PRIV-06`). Este prazo define o SLA alvo.

---

### 3.2 Viagens — 30 / 90 dias

**Inclui:** `trips`, `trip_segments`, `activities`, `meals`, `trip_users`, checklist, comentários de viagem, convites/share links, templates do usuário, revisões/propostas vinculadas.

**Gatilho:** exclusão da viagem **ou** exclusão da conta titular (cascade).

**Fluxo proposto:**
1. Hard-delete (já usado em parte do backend) ou soft + purge ≤ 30 dias.
2. Arquivos associados em object storage (R2) removidos no mesmo ciclo.
3. Backup ≤ 90 dias.

---

### 3.3 Posts e eventos — 30 / 90 dias

**Inclui:** `events`, participantes, `event_posts` / comentários / likes / votos; posts de rede (`posts_network` DynamoDB); mensagens de chat quando vinculadas a exclusão de conta (alinhar soft-delete de `messages`).

**Gatilho:** exclusão do conteúdo pelo autor, exclusão do evento, ou exclusão de conta.

**Fluxo proposto:** soft-delete (já existe em posts/comentários de evento) → purge ≤ 30 dias → backup ≤ 90 dias.

---

### 3.4 Documentos de viagem — 7 / 90 dias (arquivos do usuário)

**Inclui:** `trip_documents` (arquivos criptografados AES-256-GCM no R2 + metadados), `user_document_expiry`.

**Não inclui:** cache DynamoDB `docs_needed_countries` (ver §3.4b); logs de visualização (ver §3.4c).

**Gatilho:** exclusão do documento, da viagem, ou da conta.

**Fluxo proposto:**
1. Remoção do objeto no R2 e registro no banco em até **7 dias** no sistema ativo (preferencialmente **imediata** na exclusão; 7 dias é o teto de grace/purge).
2. Backup / snapshots ≤ **90 dias**.

**Criptografia:** bytes em claro só existem em trânsito (TLS) e na memória da API no momento do upload/view autenticado. No R2 o objeto é ciphertext; visualização passa por `GET .../documents/{id}/content` (sem URL presignada em claro).

### 3.4c Logs de visualização de documentos — fim da viagem + 3 meses

**Inclui:** objetos JSON em R2 `audit/document-views/exp/{yyyy}/{MM}/{dd}/…` (quem abriu qual documento, quando, IP se disponível).

**Gatilho de retenção:** `retain_until = end_date da viagem + 3 meses` (se sem `end_date`, data da visualização + 3 meses).

**Por que R2:** volume baixo, leitura raríssima (compliance); custo de storage ~zero vs. tabela Neon.

**Purge:** `POST /api/v1/internal/retention/purge-document-view-audits` com `X-Internal-Secret` (EventBridge mensal/diário).

### 3.4b Caches DynamoDB (travel-docs / location-info) — ~182 dias

**Inclui:** `docs_needed_countries`, `travel-location-info` (Lambda AI).

**Regra:** TTL de **~182 dias** no código; **sem PITR / backup intencional**. Política de privacidade deve refletir esse prazo. Habilitar TTL em `docs_needed_countries` se ainda estiver off.

---

### 3.5 Coordenadas no roteiro vs cache de consulta

**Roteiro / evento (persistido pelo usuário):** lat/lng em activities, meals, events → seguem retenção da **viagem** ou do **evento** (30 / 90).

**Cache de consulta (`travel-location-info`):** TTL **~182 dias**; sem backup intencional (sem PITR).

---

### 3.6 Logs operacionais comuns — 90 dias

**Inclui:** logs de aplicação/API, erros, `email_notification_log`, notificações operacionais, logs de geração AI de uso comum (`ai_generations` quando não forem registro fiscal/obrigatório), métricas não obrigatórias.

**Regra:** retenção de **90 dias** no sistema ativo; extensão em backup só se necessário à segurança/investigação, com revisão periódica.

---

### 3.7 Registros obrigatórios de acesso — 6 meses

**Inclui:** logs de autenticação/acesso necessários a segurança, auditoria mínima, eventual obrigação contratual/regulatória; trilha de auditoria B2B sensível (`b2b_trip_logs`) quando classificada como registro obrigatório.

**Regra:** **6 meses** no sistema ativo; cópias em arquivo sob **controle e sigilo** (acesso restrito, sem uso para produto).

---

### 3.8 Dados de cobrança — obrigação legal / a definir com contador

**Inclui:** `trip_payments`, `trip_unlocks`, `stripe_events`, status de assinatura/plano. Cartão completo fica na Stripe (não armazenamos PAN).

**Regra provisória:**
- Manter o mínimo para status do plano e suporte.
- Prazo fiscal/contábil: **a definir com contador** (tipicamente anos no Brasil — não inventar número na política até fechar).
- Na Política de Privacidade: linguagem “conforme obrigação legal e regras fiscais/contábeis aplicáveis”.

---

### 3.9 Disputa ou fraude — enquanto necessário / acesso restrito

**Inclui:** qualquer categoria acima marcada/congelada por disputa, chargeback, abuso, investigação de segurança ou processo.

**Regra:** **suspende** os prazos de purge das categorias 3.1–3.7 enquanto o caso exigir; acesso apenas a papéis autorizados; revisão ao encerrar o caso.

---

## 4. Texto sugerido para a Política de Privacidade (§ 8)

Substituir a seção qualitativa atual por algo nesta linha (PT-BR canônico):

> Mantemos dados pelo tempo necessário às finalidades desta Política. **Enquanto a conta e o conteúdo estiverem ativos**, os dados permanecem disponíveis para prestar o serviço. **Após exclusão** (conta, viagem, post, documento etc.) ou quando a finalidade encerrar, aplicamos, em regra, os prazos abaixo no sistema ativo, e cópias em backup ou arquivo restrito por período técnico adicional:
>
> | Categoria | Sistema ativo (após exclusão / fim da necessidade) | Backup / arquivo restrito |
> |---|---|---|
> | Conta e perfil | até 30 dias | até 90 dias |
> | Viagens | até 30 dias | até 90 dias |
> | Posts e eventos | até 30 dias | até 90 dias |
> | Documentos de viagem (arquivos) | até 7 dias | até 90 dias |
> | Coordenadas / location-info (cache) | até ~182 dias | sem backup intencional |
> | Docs de referência por país (cache) | até ~182 dias | sem backup intencional |
> | Logs operacionais comuns | 90 dias | conforme necessidade de segurança |
> | Registros obrigatórios de acesso | 6 meses | sob controle e sigilo |
> | Dados de cobrança | conforme obrigação legal e regras fiscais/contábeis | conforme orientação contábil |
> | Dados ligados a disputa ou fraude | enquanto necessários ao caso | acesso restrito |
>
> Coordenadas **salvas como parte de roteiros ou eventos** seguem a retenção da viagem/evento correspondente. Backups podem reter cópias por até o prazo da coluna de arquivo após a exclusão lógica. Podemos reter dados por prazo maior quando houver obrigação legal, ordem competente ou necessidade legítima de segurança/prevenção a fraude.

---

## 5. Checklist de implementação (engenharia)

| ID | Item | Status alvo |
|---|---|---|
| RET-01 | Job de purge de conta soft-deleted ≤ 30 dias | A implementar (PRIV-06) |
| RET-02 | Cascade de purge: preferências, follows, chat, posts, viagens | A implementar |
| RET-03 | Purge de `trip_documents` + R2 ≤ 7 dias após exclusão | A implementar / reforçar |
| RET-04 | Caches DynamoDB location-info / travel-docs | Mantido **182 dias**; política de privacidade acompanha |
| RET-05 | CloudWatch: 90 dias (ops) / 180 dias (acesso, se separado) | **CLI** em [`RETENCAO_INFRA.md`](./RETENCAO_INFRA.md) — log groups já existem fora do SAM |
| RET-06 | Neon history + backups Dynamo/produto ≤ 90 dias | Configurar no console — ver [`RETENCAO_INFRA.md`](./RETENCAO_INFRA.md) |
| RET-06b | R2 lifecycle: abort multipart 7d; noncurrent 90d se versioning | Configurar no Cloudflare — ver runbook |
| RET-07 | Flag “hold por disputa/fraude” que bloqueia purge | A implementar |
| RET-08 | Atualizar §8 da Política (pt/en/es/fr) com a tabela | Fora do escopo de infra (você) |
| RET-09 | Fechar prazo fiscal de cobrança com contador | Pendente jurídico/contábil |

**Runbook de nuvem:** [`RETENCAO_INFRA.md`](./RETENCAO_INFRA.md)

---

## 6. Decisões em aberto

1. **Cobrança:** prazo exato (anos) após definição com contador — até lá, não fixar número na política pública.
2. **Chat:** mensagens seguem posts/eventos (30/90) ou regra própria? Recomendação: **mesmo prazo de posts** após exclusão de conta; exclusão individual de mensagem = soft + purge ≤ 30 dias.
3. **Analytics de terceiros:** prazo configurado na ferramenta; informar no banner/configurações (já previsto na política).
4. **Pré-login / rascunho:** retenção limitada no cliente; se enviado à API sem conta, não persistir além do necessário à geração (alinhar a coordenadas/consulta).

---

## 7. Histórico

| Data | Alteração |
|---|---|
| 2026-08-04 | Versão inicial com prazos da política de retenção recomendada; mapeamento às entidades Baggagi. |
| 2026-08-04 | Runbook de infra (`RETENCAO_INFRA.md`); caches DynamoDB mantidos em ~182 dias. |
