package org.example.infrastructure.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.domain.entity.Post;
import org.example.domain.entity.PostComment;
import org.example.domain.entity.PostLike;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Repositório para a tabela DynamoDB {@code posts_network}.
 *
 * <h2>Modelo de dados (single-table)</h2>
 * <pre>
 * PK (postId)       | SK                       | Tipo
 * ------------------+--------------------------+--------
 * post-&lt;uuid&gt;       | POST                     | Postagem
 * post-&lt;uuid&gt;       | COMMENT#&lt;commentId&gt;      | Comentário
 * post-&lt;uuid&gt;       | LIKE#&lt;userId&gt;            | Like
 * </pre>
 *
 * <h2>GSI para listagem por autor</h2>
 * <pre>
 * Índice: authorId-postedAt-index
 *   GSI PK: authorId  (String)
 *   GSI SK: postedAt  (String ISO-8601 — ordena corretamente)
 * Projeção: ALL
 * Obs.: sparse index — apenas itens com SK=POST possuem ambos os atributos.
 * </pre>
 */
@Slf4j
@ApplicationScoped
public class PostDynamoRepository {

    /**
     * Resultado paginado de uma query de listagem.
     *
     * @param items     Lista de posts desta página.
     * @param nextToken Cursor opaco (Base64-JSON do LastEvaluatedKey do DynamoDB) para a próxima
     *                  página. {@code null} quando não há mais resultados.
     */
    public record PagedResult(List<Post> items, String nextToken) {}

    private static final String PK = "id_post";   // partition key real da tabela posts_network
    private static final String SK = "sk";
    private static final String SK_POST_ROOT = "POST";
    private static final String SK_COMMENT_PREFIX = "COMMENT#";
    private static final String SK_LIKE_PREFIX = "LIKE#";

    private final DynamoDbClient dynamo;
    private final String tableName;

    @Inject
    public PostDynamoRepository(
            DynamoDbClient dynamo,
            @ConfigProperty(name = "aws.dynamodb.posts-table", defaultValue = "posts_network")
            String tableName) {
        this.dynamo = dynamo;
        this.tableName = tableName;
        try {
            var desc = dynamo.describeTable(DescribeTableRequest.builder()
                    .tableName(tableName)
                    .build());
            var keySchema = desc.table().keySchema();
            log.info("[DynamoDB] Table '{}' KeySchema: {}", tableName, keySchema);
            var gsi = desc.table().globalSecondaryIndexes();
            if (gsi != null && !gsi.isEmpty()) {
                for (var index : gsi) {
                    log.info("[DynamoDB] GSI '{}' KeySchema: {}", index.indexName(), index.keySchema());
                }
            }
        } catch (Exception e) {
            log.error("[DynamoDB] Failed to describe table '{}': {}", tableName, e.getMessage());
        }
    }

    // =========================================================================
    // Posts — CRUD
    // =========================================================================

    public Post savePost(Post post) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(PK, av(post.getPostId()));          // id_post
        item.put(SK, av(SK_POST_ROOT));
        item.put("authorId", av(post.getAuthorId()));
        item.put("authorName", av(safe(post.getAuthorName())));
        item.put("text", av(post.getText()));
        item.put("postedAt", av(post.getPostedAt()));
        item.put("updatedAt", av(post.getUpdatedAt()));
        item.put("likeCount", avN("0"));
        item.put("commentCount", avN("0"));

        if (post.getImageUrl() != null && !post.getImageUrl().isBlank()) {
            item.put("imageUrl", av(post.getImageUrl()));
        }
        if (post.getLocation() != null && !post.getLocation().isBlank()) {
            item.put("location", av(post.getLocation()));
        }
        if (post.getTags() != null && !post.getTags().isEmpty()) {
            item.put("tags", AttributeValue.builder()
                    .ss(post.getTags())
                    .build());
        }

        dynamo.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build());

        log.debug("Post saved postId={}", post.getPostId());
        return post;
    }

    public Optional<Post> findPostById(String postId) {
        var resp = dynamo.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        PK, av(postId),
                        SK, av(SK_POST_ROOT)))
                .build());

        if (!resp.hasItem() || resp.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(itemToPost(resp.item()));
    }

    public Post updatePost(Post post) {
        // Recria o item preservando likeCount/commentCount com ADD expressions
        var updates = new HashMap<String, AttributeValueUpdate>();
        updates.put("authorName", put(av(safe(post.getAuthorName()))));
        updates.put("text", put(av(post.getText())));
        updates.put("updatedAt", put(av(post.getUpdatedAt())));

        if (post.getImageUrl() != null && !post.getImageUrl().isBlank()) {
            updates.put("imageUrl", put(av(post.getImageUrl())));
        }
        if (post.getLocation() != null && !post.getLocation().isBlank()) {
            updates.put("location", put(av(post.getLocation())));
        }
        if (post.getTags() != null && !post.getTags().isEmpty()) {
            updates.put("tags", put(AttributeValue.builder().ss(post.getTags()).build()));
        }

        dynamo.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        PK, av(post.getPostId()),
                        SK, av(SK_POST_ROOT)))
                .attributeUpdates(updates)
                .build());

        log.debug("Post updated postId={}", post.getPostId());
        return post;
    }

    public void deletePost(String postId) {
        // Remove postagem + todos os filhos (comentários + likes) via query + batch delete
        var items = queryAllItems(postId);
        if (items.isEmpty()) {
            return;
        }

        List<WriteRequest> deletes = items.stream()
                .map(item -> WriteRequest.builder()
                        .deleteRequest(DeleteRequest.builder()
                                .key(Map.of(
                                        PK, item.get(PK),
                                        SK, item.get(SK)))
                                .build())
                        .build())
                .collect(Collectors.toList());

        // DynamoDB batch write aceita até 25 itens por chamada
        for (int i = 0; i < deletes.size(); i += 25) {
            List<WriteRequest> batch = deletes.subList(i, Math.min(i + 25, deletes.size()));
            dynamo.batchWriteItem(BatchWriteItemRequest.builder()
                    .requestItems(Map.of(tableName, batch))
                    .build());
        }
        log.debug("Post and all children deleted postId={}", postId);
    }

    // =========================================================================
    // Likes
    // =========================================================================

    /**
     * Adiciona ou remove o like de um usuário (toggle).
     *
     * @return {@code true} se o like foi adicionado, {@code false} se foi removido.
     */
    public boolean toggleLike(String postId, String userId) {
        String sk = SK_LIKE_PREFIX + userId;
        var existing = dynamo.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(PK, av(postId), SK, av(sk)))
                .build());

        if (existing.hasItem() && !existing.item().isEmpty()) {
            // Remove like
            dynamo.deleteItem(DeleteItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of(PK, av(postId), SK, av(sk)))
                    .build());
            decrementCounter(postId, "likeCount");
            log.debug("Like removed postId={} userId={}", postId, userId);
            return false;
        } else {
            // Adiciona like
            dynamo.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(Map.of(
                            PK, av(postId),
                            SK, av(sk),
                            "userId", av(userId),
                            "likedAt", av(Instant.now().toString())))
                    .build());
            incrementCounter(postId, "likeCount");
            log.debug("Like added postId={} userId={}", postId, userId);
            return true;
        }
    }

    /** Verifica se um usuário já curtiu a postagem. */
    public boolean hasLiked(String postId, String userId) {
        var resp = dynamo.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(PK, av(postId), SK, av(SK_LIKE_PREFIX + userId)))
                .build());
        return resp.hasItem() && !resp.item().isEmpty();
    }

    // =========================================================================
    // Comentários
    // =========================================================================

    public PostComment addComment(PostComment comment) {
        String sk = SK_COMMENT_PREFIX + comment.getCommentId();
        dynamo.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(Map.of(
                        PK, av(comment.getPostId()),
                        SK, av(sk),
                        "commentId", av(comment.getCommentId()),
                        "authorId", av(comment.getAuthorId()),
                        "authorName", av(safe(comment.getAuthorName())),
                        "text", av(comment.getText()),
                        "createdAt", av(comment.getCreatedAt())))
                .build());
        incrementCounter(comment.getPostId(), "commentCount");
        log.debug("Comment added postId={} commentId={}", comment.getPostId(), comment.getCommentId());
        return comment;
    }

    public void deleteComment(String postId, String commentId) {
        dynamo.deleteItem(DeleteItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(PK, av(postId), SK, av(SK_COMMENT_PREFIX + commentId)))
                .build());
        decrementCounter(postId, "commentCount");
        log.debug("Comment deleted postId={} commentId={}", postId, commentId);
    }

    public List<PostComment> listComments(String postId) {
        var resp = dynamo.query(QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("#pk = :pk AND begins_with(#sk, :skPrefix)")
                .expressionAttributeNames(Map.of("#pk", PK, "#sk", SK))
                .expressionAttributeValues(Map.of(
                        ":pk", av(postId),
                        ":skPrefix", av(SK_COMMENT_PREFIX)))
                .build());

        return resp.items().stream()
                .map(this::itemToComment)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // Listagem paginada por autor (GSI: authorId-postedAt-index)
    // =========================================================================

    private static final String GSI_AUTHOR_INDEX = "authorId-postedAt-index";
    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_LIMIT = 20;

    /**
     * Lista posts de um único autor, ordenados do mais recente para o mais antigo.
     *
     * <p>Usa o GSI {@code authorId-postedAt-index}. O {@code nextToken} é o
     * {@code LastEvaluatedKey} do DynamoDB serializado como Base64-JSON — passe-o
     * diretamente de volta para obter a próxima página (cursor opaco para o cliente).
     *
     * @param authorId   ID do autor.
     * @param limit      Número máximo de posts (1–50, default 20).
     * @param nextToken  Cursor da página anterior ({@code null} = primeira página).
     * @return           Página de resultados com cursor para a próxima página.
     */
    public PagedResult listByAuthor(String authorId, int limit, String nextToken) {
        int pageSize = clampLimit(limit);

        var reqBuilder = QueryRequest.builder()
                .tableName(tableName)
                .indexName(GSI_AUTHOR_INDEX)
                .keyConditionExpression("#author = :authorId")
                .expressionAttributeNames(Map.of("#author", "authorId"))
                .expressionAttributeValues(Map.of(":authorId", av(authorId)))
                .scanIndexForward(false)   // DESC — mais recentes primeiro
                .limit(pageSize);

        if (nextToken != null && !nextToken.isBlank()) {
            Map<String, AttributeValue> startKey = decodeNextToken(nextToken);
            if (startKey != null) {
                reqBuilder.exclusiveStartKey(startKey);
            }
        }

        QueryResponse resp = dynamo.query(reqBuilder.build());

        List<Post> posts = resp.items().stream()
                .filter(item -> SK_POST_ROOT.equals(str(item, SK))) // filtra apenas raízes
                .map(this::itemToPost)
                .collect(Collectors.toList());

        String cursor = resp.hasLastEvaluatedKey() && !resp.lastEvaluatedKey().isEmpty()
                ? encodeNextToken(resp.lastEvaluatedKey())
                : null;

        log.debug("listByAuthor authorId={} returned={} hasMore={}", authorId, posts.size(), cursor != null);
        return new PagedResult(posts, cursor);
    }

    /**
     * Lista posts de múltiplos autores (feed de seguidos), ordenados do mais recente ao mais antigo.
     *
     * <p>Estratégia: query paralela por autor no GSI com filtro temporal {@code before},
     * depois merge-sort em memória. Adequado para listas de seguidos de até ~200 autores.
     *
     * @param authorIds  IDs dos autores a consultar.
     * @param limit      Número máximo de posts a retornar na página (1–50).
     * @param before     Cursor ISO-8601 — retorna apenas posts com {@code postedAt < before}.
     *                   {@code null} = sem limite de tempo (primeira página).
     * @return           Página de resultados. O campo {@code nextToken} é o {@code postedAt}
     *                   do último item retornado (timestamp ISO-8601); passe-o como {@code before}
     *                   na próxima chamada.
     */
    public PagedResult listByAuthors(List<String> authorIds, int limit, String before) {
        if (authorIds == null || authorIds.isEmpty()) {
            return new PagedResult(List.of(), null);
        }
        int pageSize = clampLimit(limit);
        // Busca pageSize * 2 por autor para ter margem após o merge (evita páginas vazias)
        int perAuthorLimit = Math.min(pageSize * 2, MAX_LIMIT);

        List<Post> merged = new ArrayList<>();

        for (String authorId : authorIds) {
            var reqBuilder = QueryRequest.builder()
                    .tableName(tableName)
                    .indexName(GSI_AUTHOR_INDEX)
                    .keyConditionExpression("#author = :authorId")
                    .expressionAttributeNames(Map.of("#author", "authorId"))
                    .expressionAttributeValues(Map.of(":authorId", av(authorId)))
                    .scanIndexForward(false)
                    .limit(perAuthorLimit);

            if (before != null && !before.isBlank()) {
                // Aplica filtro temporal via KeyConditionExpression (GSI SK é postedAt)
                reqBuilder
                        .keyConditionExpression("#author = :authorId AND #postedAt < :before")
                        .expressionAttributeNames(Map.of("#author", "authorId", "#postedAt", "postedAt"))
                        .expressionAttributeValues(Map.of(
                                ":authorId", av(authorId),
                                ":before", av(before)));
            }

            try {
                QueryResponse resp = dynamo.query(reqBuilder.build());
                resp.items().stream()
                        .filter(item -> SK_POST_ROOT.equals(str(item, SK)))
                        .map(this::itemToPost)
                        .forEach(merged::add);
            } catch (Exception e) {
                log.warn("Feed query failed for authorId={}: {}", authorId, e.getMessage());
            }
        }

        // Merge-sort: mais recentes primeiro (ISO-8601 ordena corretamente como String)
        merged.sort(Comparator.comparing(
                p -> p.getPostedAt() != null ? p.getPostedAt() : "",
                Comparator.reverseOrder()));

        List<Post> page = merged.stream().limit(pageSize).collect(Collectors.toList());

        // Cursor = postedAt do último item da página atual (passa como `before` na próxima chamada)
        String nextCursor = page.isEmpty() ? null : page.get(page.size() - 1).getPostedAt();
        // Se retornamos menos que o tamanho da página, não há mais resultados
        if (page.size() < pageSize) {
            nextCursor = null;
        }

        log.debug("listByAuthors authors={} returned={} nextCursor={}",
                authorIds.size(), page.size(), nextCursor);
        return new PagedResult(page, nextCursor);
    }

    // =========================================================================
    // Helpers internos
    // =========================================================================

    private List<Map<String, AttributeValue>> queryAllItems(String postId) {
        var resp = dynamo.query(QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("#pk = :pk")
                .expressionAttributeNames(Map.of("#pk", PK))
                .expressionAttributeValues(Map.of(":pk", av(postId)))
                .build());
        return resp.items();
    }

    private void incrementCounter(String postId, String field) {
        adjustCounter(postId, field, 1);
    }

    private void decrementCounter(String postId, String field) {
        adjustCounter(postId, field, -1);
    }

    private void adjustCounter(String postId, String field, int delta) {
        try {
            dynamo.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of(PK, av(postId), SK, av(SK_POST_ROOT)))
                    .updateExpression("ADD #f :delta")
                    .expressionAttributeNames(Map.of("#f", field))
                    .expressionAttributeValues(Map.of(":delta", avN(String.valueOf(delta))))
                    .build());
        } catch (Exception e) {
            log.warn("Failed to adjust counter field={} postId={}: {}", field, postId, e.getMessage());
        }
    }

    private Post itemToPost(Map<String, AttributeValue> item) {
        return Post.builder()
                .postId(str(item, PK))
                .authorId(str(item, "authorId"))
                .authorName(str(item, "authorName"))
                .text(str(item, "text"))
                .location(str(item, "location"))
                .imageUrl(str(item, "imageUrl"))
                .postedAt(str(item, "postedAt"))
                .updatedAt(str(item, "updatedAt"))
                .likeCount(num(item, "likeCount"))
                .commentCount(num(item, "commentCount"))
                .tags(item.containsKey("tags") && item.get("tags").ss() != null
                        ? new ArrayList<>(item.get("tags").ss()) : List.of())
                .build();
    }

    private PostComment itemToComment(Map<String, AttributeValue> item) {
        return PostComment.builder()
                .commentId(str(item, "commentId"))
                .postId(str(item, PK))
                .authorId(str(item, "authorId"))
                .authorName(str(item, "authorName"))
                .text(str(item, "text"))
                .createdAt(str(item, "createdAt"))
                .build();
    }

    private static AttributeValue av(String value) {
        return AttributeValue.builder().s(value != null ? value : "").build();
    }

    private static AttributeValue avN(String value) {
        return AttributeValue.builder().n(value).build();
    }

    private static AttributeValueUpdate put(AttributeValue av) {
        return AttributeValueUpdate.builder()
                .value(av)
                .action(AttributeAction.PUT)
                .build();
    }

    private static String str(Map<String, AttributeValue> item, String key) {
        AttributeValue av = item.get(key);
        return av != null ? av.s() : null;
    }

    private static long num(Map<String, AttributeValue> item, String key) {
        AttributeValue av = item.get(key);
        if (av == null || av.n() == null) return 0L;
        try { return Long.parseLong(av.n()); } catch (NumberFormatException e) { return 0L; }
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }

    private static int clampLimit(int requested) {
        if (requested <= 0) return DEFAULT_LIMIT;
        return Math.min(requested, MAX_LIMIT);
    }

    /**
     * Serializa o {@code LastEvaluatedKey} do DynamoDB como JSON Base64-URL para uso como cursor.
     * O formato interno é: {@code {"postId":"...","sk":"...","authorId":"...","postedAt":"..."}}.
     */
    private static String encodeNextToken(Map<String, AttributeValue> lastKey) {
        try {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, AttributeValue> e : lastKey.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                String val = e.getValue().s() != null ? e.getValue().s()
                        : (e.getValue().n() != null ? e.getValue().n() : "");
                sb.append("\"").append(e.getKey()).append("\":\"").append(val).append("\"");
            }
            sb.append("}");
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Deserializa o cursor Base64-JSON de volta para o {@code ExclusiveStartKey} do DynamoDB.
     * Retorna {@code null} em caso de token inválido (DynamoDB simplesmente ignora).
     */
    private static Map<String, AttributeValue> decodeNextToken(String token) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token.trim());
            String json = new String(decoded, StandardCharsets.UTF_8);
            // Parser mínimo (sem dependência extra): extrai pares "key":"value"
            Map<String, AttributeValue> result = new LinkedHashMap<>();
            // Remove { e }
            json = json.trim();
            if (json.startsWith("{")) json = json.substring(1);
            if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

            for (String pair : json.split(",(?=\")")) {
                pair = pair.trim();
                int colon = pair.indexOf("\":\"");
                if (colon < 0) continue;
                String key = pair.substring(1, colon);
                String val = pair.substring(colon + 3);
                if (val.endsWith("\"")) val = val.substring(0, val.length() - 1);
                // Atributos numéricos (likeCount, commentCount) são N; outros são S
                if (key.equals("likeCount") || key.equals("commentCount")) {
                    result.put(key, AttributeValue.builder().n(val).build());
                } else {
                    result.put(key, AttributeValue.builder().s(val).build());
                }
            }
            return result.isEmpty() ? null : result;
        } catch (Exception ex) {
            return null;
        }
    }
}
