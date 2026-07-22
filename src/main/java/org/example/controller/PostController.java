package org.example.controller;

import java.util.UUID;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.example.application.dto.document.UploadDocumentRequest;
import org.example.application.services.PostService;
import org.example.application.services.PostService.PostForbiddenException;
import org.example.application.services.PostService.PostNotFoundException;
import org.example.application.services.TokenService;
import org.example.domain.entity.Post;
import org.example.domain.entity.PostComment;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.auth.NeonAuthJwtVerifier;
import org.example.infrastructure.storage.ObjectStorageService;
import org.example.utils.AuthTokenException;
import org.example.utils.DocumentUploadSupport;
import org.example.utils.JwtAuthSupport;
import org.example.utils.RequestAuthHeaders;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Endpoints REST para postagens da rede social.
 *
 * <p>Tabela DynamoDB: {@code posts_network}
 * (ARN: {@code arn:aws:dynamodb:sa-east-1:252186483726:table/posts_network})
 *
 * <h2>Rotas disponíveis</h2>
 * <pre>
 * POST   /api/v1/posts                          — criar postagem
 * POST   /api/v1/posts/image-upload-request     — URL presignada R2 para imagem da postagem
 * PUT    /api/v1/posts/{id}                     — editar postagem (só autor)
 * DELETE /api/v1/posts/{id}                     — apagar postagem (só autor)
 * GET    /api/v1/posts/{id}                     — buscar postagem por ID
 * GET    /api/v1/posts/user/{authorId}          — posts de um autor (paginado, mais recente→antigo)
 * GET    /api/v1/posts/feed                     — feed de múltiplos autores (paginado)
 * POST   /api/v1/posts/{id}/likes              — like/unlike (toggle)
 * GET    /api/v1/posts/{id}/likes/me           — verifica se usuário atual curtiu
 * POST   /api/v1/posts/{id}/comments           — adicionar comentário
 * GET    /api/v1/posts/{id}/comments           — listar comentários
 * DELETE /api/v1/posts/{id}/comments/{cid}     — apagar comentário
 * </pre>
 */
@Slf4j
@Tag(name = "Posts", description = "Criação, edição, remoção de postagens e interações (likes e comentários)")
@Path("/api/v1/posts")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final NeonAuthJwtVerifier neonAuthJwtVerifier;
    private final ObjectStorageService objectStorageService;

    // =========================================================================
    // POST — Solicitar upload de imagem (R2)
    // =========================================================================

    @POST
    @Path("/image-upload-request")
    @Operation(
        summary = "Solicitar upload de imagem de postagem",
        description = "Gera uma URL presignada para o frontend enviar a imagem da postagem " +
                      "diretamente ao Cloudflare R2. Apenas imagens (JPEG, PNG, WebP, GIF)."
    )
    @APIResponses({
        @APIResponse(responseCode = "201", description = "URL presignada gerada com sucesso"),
        @APIResponse(responseCode = "400", description = "Dados inválidos ou tipo não suportado"),
        @APIResponse(responseCode = "401", description = "Token ausente ou inválido"),
        @APIResponse(responseCode = "503", description = "Storage não configurado"),
        @APIResponse(responseCode = "500", description = "Erro interno")
    })
    public Response imageUploadRequest(
            @RequestBody(description = "Nome do arquivo e content type", required = true)
                    UploadDocumentRequest req,
            @Context HttpHeaders headers) {

        AuthResult auth = resolveAuth(headers);
        if (!auth.authenticated()) {
            return unauthorized("Token ausente ou inválido");
        }

        if (!objectStorageService.isConfigured()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(errorBody("STORAGE_NOT_CONFIGURED", "Document storage is not configured"))
                    .build();
        }

        if (req == null) {
            return badRequest("VALIDATION_ERROR", "Request body is required");
        }

        Optional<DocumentUploadSupport.ResolvedUpload> resolved =
                DocumentUploadSupport.resolve(req.getFileName(), req.getContentType());
        if (resolved.isEmpty()) {
            String msg = DocumentUploadSupport.unsupportedTypeMessage(
                    req.getContentType(), req.getFileName());
            return badRequest("UNSUPPORTED_CONTENT_TYPE", msg);
        }

        DocumentUploadSupport.ResolvedUpload upload = resolved.get();
        if (!upload.contentType().startsWith("image/")) {
            return badRequest(
                    "UNSUPPORTED_CONTENT_TYPE",
                    "Only image uploads are allowed for posts (JPEG, PNG, WebP, GIF)");
        }

        String extension = DocumentUploadSupport.extractExtension(upload.fileName());
        String s3Key = "posts/" + auth.userId() + "/post-" + UUID.randomUUID() + extension;

        try {
            String uploadUrl = objectStorageService.presignPut(s3Key, upload.contentType());
            String publicUrl = objectStorageService.getPublicUrl(s3Key);

            var body = Map.of(
                    "uploadUrl", uploadUrl,
                    "s3Key", s3Key,
                    "publicUrl", publicUrl,
                    "expiresInSeconds", objectStorageService.getUploadPresignSeconds()
            );

            log.info("POST /posts/image-upload-request 201 userId={} s3Key={}", auth.userId(), s3Key);
            return Response.status(Response.Status.CREATED).entity(body).build();
        } catch (Exception e) {
            log.error("Post image upload request failed userId={}", auth.userId(), e);
            return internalError("Erro ao gerar URL presignada: " + e.getMessage());
        }
    }

    // =========================================================================
    // POST — Criar postagem
    // =========================================================================

    @POST
    @Operation(
        summary = "Criar postagem",
        description = "Cria uma nova postagem na rede social. O campo `postedAt` é opcional " +
                      "(se omitido, usa o horário do servidor). " +
                      "O autor é identificado pelo token JWT."
    )
    @APIResponses({
        @APIResponse(responseCode = "201", description = "Postagem criada com sucesso"),
        @APIResponse(responseCode = "400", description = "Dados inválidos"),
        @APIResponse(responseCode = "401", description = "Token ausente ou inválido"),
        @APIResponse(responseCode = "500", description = "Erro interno")
    })
    public Response createPost(
            @RequestBody(description = "Dados da postagem", required = true) CreatePostRequest body,
            @Context HttpHeaders headers) {

        if (body == null || body.getText() == null || body.getText().isBlank()) {
            return badRequest("TEXT_REQUIRED", "O campo 'text' é obrigatório");
        }

        AuthResult auth = resolveAuth(headers);
        if (!auth.authenticated()) {
            return unauthorized("Token ausente ou inválido");
        }

        try {
            Post created = postService.createPost(new PostService.CreatePostCommand(
                    auth.userId(),
                    auth.displayName(),
                    body.getText(),
                    body.getTags(),
                    body.getLocation(),
                    body.getPostedAt(),
                    body.getImageUrl()
            ));
            log.info("POST /posts 201 postId={} authorId={}", created.getPostId(), created.getAuthorId());
            return Response.status(Response.Status.CREATED).entity(toResponse(created)).build();
        } catch (Exception e) {
            log.error("POST /posts 500: {}", e.getMessage(), e);
            return internalError("Erro ao criar postagem");
        }
    }

    // =========================================================================
    // PUT — Editar postagem
    // =========================================================================

    @PUT
    @Path("/{postId}")
    @Operation(
        summary = "Editar postagem",
        description = "Atualiza o conteúdo de uma postagem existente. Apenas o autor pode editar."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Postagem atualizada"),
        @APIResponse(responseCode = "400", description = "Dados inválidos"),
        @APIResponse(responseCode = "401", description = "Token ausente ou inválido"),
        @APIResponse(responseCode = "403", description = "Usuário não é o autor da postagem"),
        @APIResponse(responseCode = "404", description = "Postagem não encontrada"),
        @APIResponse(responseCode = "500", description = "Erro interno")
    })
    public Response updatePost(
            @PathParam("postId") String postId,
            @RequestBody(description = "Campos a atualizar", required = true) UpdatePostRequest body,
            @Context HttpHeaders headers) {

        if (body == null || body.getText() == null || body.getText().isBlank()) {
            return badRequest("TEXT_REQUIRED", "O campo 'text' é obrigatório");
        }

        AuthResult auth = resolveAuth(headers);
        if (!auth.authenticated()) {
            return unauthorized("Token ausente ou inválido");
        }

        try {
            Post updated = postService.updatePost(postId, auth.userId(),
                    new PostService.UpdatePostCommand(
                            auth.displayName(),
                            body.getText(),
                            body.getTags(),
                            body.getLocation(),
                            body.getImageUrl()
                    ));
            log.info("PUT /posts/{} 200 by userId={}", postId, auth.userId());
            return Response.ok(toResponse(updated)).build();
        } catch (PostNotFoundException e) {
            return notFound(e.getMessage());
        } catch (PostForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (Exception e) {
            log.error("PUT /posts/{} 500: {}", postId, e.getMessage(), e);
            return internalError("Erro ao atualizar postagem");
        }
    }

    // =========================================================================
    // DELETE — Apagar postagem
    // =========================================================================

    @DELETE
    @Path("/{postId}")
    @Operation(
        summary = "Apagar postagem",
        description = "Remove permanentemente uma postagem e todos os seus comentários e likes. " +
                      "Apenas o autor pode apagar."
    )
    @APIResponses({
        @APIResponse(responseCode = "204", description = "Postagem removida"),
        @APIResponse(responseCode = "401", description = "Token ausente ou inválido"),
        @APIResponse(responseCode = "403", description = "Usuário não é o autor"),
        @APIResponse(responseCode = "404", description = "Postagem não encontrada"),
        @APIResponse(responseCode = "500", description = "Erro interno")
    })
    public Response deletePost(
            @PathParam("postId") String postId,
            @Context HttpHeaders headers) {

        AuthResult auth = resolveAuth(headers);
        if (!auth.authenticated()) {
            return unauthorized("Token ausente ou inválido");
        }

        try {
            postService.deletePost(postId, auth.userId());
            log.info("DELETE /posts/{} 204 by userId={}", postId, auth.userId());
            return Response.noContent().build();
        } catch (PostNotFoundException e) {
            return notFound(e.getMessage());
        } catch (PostForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (Exception e) {
            log.error("DELETE /posts/{} 500: {}", postId, e.getMessage(), e);
            return internalError("Erro ao apagar postagem");
        }
    }

    // =========================================================================
    // GET — Buscar postagem por ID
    // =========================================================================

    @GET
    @Path("/{postId}")
    @Operation(
        summary = "Buscar postagem por ID",
        description = "Retorna os dados de uma postagem específica, incluindo contadores de likes e comentários."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Postagem encontrada"),
        @APIResponse(responseCode = "401", description = "Token ausente ou inválido"),
        @APIResponse(responseCode = "404", description = "Postagem não encontrada")
    })
    public Response getPost(
            @PathParam("postId") String postId,
            @Context HttpHeaders headers) {

        AuthResult auth = resolveAuth(headers);
        if (!auth.authenticated()) {
            return unauthorized("Token ausente ou inválido");
        }

        Optional<Post> found = postService.findById(postId);
        if (found.isEmpty()) {
            return notFound("Postagem não encontrada: " + postId);
        }

        PostResponse resp = toResponse(found.get());
        resp.setLikedByMe(postService.hasLiked(postId, auth.userId()));
        return Response.ok(resp).build();
    }

    // =========================================================================
    // POST — Like / Unlike (toggle)
    // =========================================================================

    @POST
    @Path("/{postId}/likes")
    @Operation(
        summary = "Curtir / descurtir postagem (toggle)",
        description = "Alterna o like do usuário autenticado na postagem. " +
                      "Se ainda não curtiu, adiciona o like; se já curtiu, remove. " +
                      "O contador `likeCount` da postagem é atualizado atomicamente."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Resultado do toggle: `liked=true` ou `liked=false`"),
        @APIResponse(responseCode = "401", description = "Token ausente ou inválido"),
        @APIResponse(responseCode = "404", description = "Postagem não encontrada"),
        @APIResponse(responseCode = "500", description = "Erro interno")
    })
    public Response toggleLike(
            @PathParam("postId") String postId,
            @Context HttpHeaders headers) {

        AuthResult auth = resolveAuth(headers);
        if (!auth.authenticated()) {
            return unauthorized("Token ausente ou inválido");
        }

        try {
            boolean liked = postService.toggleLike(postId, auth.userId());
            log.info("POST /posts/{}/likes liked={} userId={}", postId, liked, auth.userId());
            return Response.ok(Map.of("liked", liked)).build();
        } catch (PostNotFoundException e) {
            return notFound(e.getMessage());
        } catch (Exception e) {
            log.error("POST /posts/{}/likes 500: {}", postId, e.getMessage(), e);
            return internalError("Erro ao processar like");
        }
    }

    // =========================================================================
    // GET — Verificar se o usuário atual curtiu a postagem
    // =========================================================================

    @GET
    @Path("/{postId}/likes/me")
    @Operation(
        summary = "Verificar se o usuário atual curtiu a postagem",
        description = "Retorna `{ \"liked\": true/false }` indicando se o usuário autenticado curtiu a postagem."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Status do like retornado"),
        @APIResponse(responseCode = "401", description = "Token ausente ou inválido"),
        @APIResponse(responseCode = "404", description = "Postagem não encontrada")
    })
    public Response hasLiked(
            @PathParam("postId") String postId,
            @Context HttpHeaders headers) {

        AuthResult auth = resolveAuth(headers);
        if (!auth.authenticated()) {
            return unauthorized("Token ausente ou inválido");
        }

        try {
            boolean liked = postService.hasLiked(postId, auth.userId());
            return Response.ok(Map.of("liked", liked)).build();
        } catch (PostNotFoundException e) {
            return notFound(e.getMessage());
        }
    }

    // =========================================================================
    // POST — Adicionar comentário
    // =========================================================================

    @POST
    @Path("/{postId}/comments")
    @Operation(
        summary = "Adicionar comentário",
        description = "Adiciona um comentário à postagem. O autor é identificado pelo token JWT."
    )
    @APIResponses({
        @APIResponse(responseCode = "201", description = "Comentário adicionado"),
        @APIResponse(responseCode = "400", description = "Texto do comentário ausente"),
        @APIResponse(responseCode = "401", description = "Token ausente ou inválido"),
        @APIResponse(responseCode = "404", description = "Postagem não encontrada"),
        @APIResponse(responseCode = "500", description = "Erro interno")
    })
    public Response addComment(
            @PathParam("postId") String postId,
            @RequestBody(description = "Texto do comentário", required = true) CommentRequest body,
            @Context HttpHeaders headers) {

        if (body == null || body.getText() == null || body.getText().isBlank()) {
            return badRequest("TEXT_REQUIRED", "O campo 'text' é obrigatório");
        }

        AuthResult auth = resolveAuth(headers);
        if (!auth.authenticated()) {
            return unauthorized("Token ausente ou inválido");
        }

        try {
            PostComment comment = postService.addComment(
                    postId, auth.userId(), auth.displayName(), body.getText());
            log.info("POST /posts/{}/comments 201 commentId={} authorId={}", postId, comment.getCommentId(), auth.userId());
            return Response.status(Response.Status.CREATED).entity(comment).build();
        } catch (PostNotFoundException e) {
            return notFound(e.getMessage());
        } catch (Exception e) {
            log.error("POST /posts/{}/comments 500: {}", postId, e.getMessage(), e);
            return internalError("Erro ao adicionar comentário");
        }
    }

    // =========================================================================
    // GET — Listar comentários
    // =========================================================================

    @GET
    @Path("/{postId}/comments")
    @Operation(
        summary = "Listar comentários da postagem",
        description = "Retorna todos os comentários de uma postagem, ordenados por data de criação (ASC)."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Lista de comentários"),
        @APIResponse(responseCode = "401", description = "Token ausente ou inválido"),
        @APIResponse(responseCode = "404", description = "Postagem não encontrada")
    })
    public Response listComments(
            @PathParam("postId") String postId,
            @Context HttpHeaders headers) {

        AuthResult auth = resolveAuth(headers);
        if (!auth.authenticated()) {
            return unauthorized("Token ausente ou inválido");
        }

        try {
            List<PostComment> comments = postService.listComments(postId);
            return Response.ok(comments).build();
        } catch (PostNotFoundException e) {
            return notFound(e.getMessage());
        } catch (Exception e) {
            log.error("GET /posts/{}/comments 500: {}", postId, e.getMessage(), e);
            return internalError(e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    // =========================================================================
    // DELETE — Apagar comentário
    // =========================================================================

    @DELETE
    @Path("/{postId}/comments/{commentId}")
    @Operation(
        summary = "Apagar comentário",
        description = "Remove um comentário da postagem. O usuário deve ser o autor do comentário."
    )
    @APIResponses({
        @APIResponse(responseCode = "204", description = "Comentário removido"),
        @APIResponse(responseCode = "401", description = "Token ausente ou inválido"),
        @APIResponse(responseCode = "404", description = "Postagem não encontrada"),
        @APIResponse(responseCode = "500", description = "Erro interno")
    })
    public Response deleteComment(
            @PathParam("postId") String postId,
            @PathParam("commentId") String commentId,
            @Context HttpHeaders headers) {

        AuthResult auth = resolveAuth(headers);
        if (!auth.authenticated()) {
            return unauthorized("Token ausente ou inválido");
        }

        try {
            postService.deleteComment(postId, commentId, auth.userId());
            log.info("DELETE /posts/{}/comments/{} 204 by userId={}", postId, commentId, auth.userId());
            return Response.noContent().build();
        } catch (PostNotFoundException e) {
            return notFound(e.getMessage());
        } catch (Exception e) {
            log.error("DELETE /posts/{}/comments/{} 500: {}", postId, commentId, e.getMessage(), e);
            return internalError("Erro ao apagar comentário");
        }
    }

    // =========================================================================
    // GET — Posts de um autor (infinite scroll, cursor DynamoDB nativo)
    // =========================================================================

    @GET
    @Path("/user/{authorId}")
    @Operation(
        summary = "Listar posts de um autor",
        description = "Retorna os posts de um usuário específico, ordenados do mais recente ao " +
                      "mais antigo. Suporta infinite scroll via cursor opaco `nextToken`.\n\n" +
                      "**Paginação:** na primeira chamada, omita `nextToken`. " +
                      "Nas chamadas seguintes, passe o `nextToken` retornado pela resposta anterior. " +
                      "Quando `nextToken` for `null` na resposta, não há mais resultados."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Página de posts"),
        @APIResponse(responseCode = "400", description = "authorId ausente"),
        @APIResponse(responseCode = "401", description = "Token ausente ou inválido")
    })
    public Response listPostsByAuthor(
            @PathParam("authorId") String authorId,
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("nextToken") String nextToken,
            @Context HttpHeaders headers) {

        AuthResult auth = resolveAuth(headers);
        if (!auth.authenticated()) {
            return unauthorized("Token ausente ou inválido");
        }
        if (authorId == null || authorId.isBlank()) {
            return badRequest("AUTHOR_REQUIRED", "authorId é obrigatório");
        }

        try {
            var result = postService.listPostsByAuthor(authorId, limit, nextToken);
            return Response.ok(toPageResponse(result)).build();
        } catch (Exception e) {
            log.error("GET /posts/user/{} 500: {}", authorId, e.getMessage(), e);
            return internalError("Erro ao listar posts do autor");
        }
    }

    // =========================================================================
    // GET — Feed de seguidos (infinite scroll, cursor temporal)
    // =========================================================================

    @GET
    @Path("/feed")
    @Operation(
        summary = "Feed de posts de múltiplos autores",
        description = "Retorna posts de uma lista de autores (ex.: pessoas que você segue), " +
                      "ordenados do mais recente ao mais antigo. Ideal para infinite scroll.\n\n" +
                      "**Parâmetro `authorIds`:** lista de IDs separados por vírgula. " +
                      "Ex.: `?authorIds=user1,user2,user3`\n\n" +
                      "**Paginação temporal:** na primeira chamada, omita `before`. " +
                      "Nas chamadas seguintes, passe o `nextToken` da resposta anterior como `before`. " +
                      "Quando `nextToken` for `null`, não há mais resultados."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Página do feed"),
        @APIResponse(responseCode = "400", description = "authorIds ausente ou vazio"),
        @APIResponse(responseCode = "401", description = "Token ausente ou inválido")
    })
    public Response listFeed(
            @QueryParam("authorIds") String authorIdsParam,
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("before") String before,
            @Context HttpHeaders headers) {

        AuthResult auth = resolveAuth(headers);
        if (!auth.authenticated()) {
            return unauthorized("Token ausente ou inválido");
        }
        if (authorIdsParam == null || authorIdsParam.isBlank()) {
            return badRequest("AUTHOR_IDS_REQUIRED", "Informe ao menos um authorId via ?authorIds=id1,id2");
        }

        List<String> authorIds = Arrays.stream(authorIdsParam.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        if (authorIds.isEmpty()) {
            return badRequest("AUTHOR_IDS_REQUIRED", "Informe ao menos um authorId válido");
        }

        try {
            var result = postService.listFeed(authorIds, limit, before);
            return Response.ok(toPageResponse(result)).build();
        } catch (Exception e) {
            log.error("GET /posts/feed 500: {}", e.getMessage(), e);
            return internalError("Erro ao carregar feed");
        }
    }

    // =========================================================================
    // Auth helpers — mesmo padrão dos outros controllers
    // =========================================================================

    private AuthResult resolveAuth(HttpHeaders headers) {
        String bearerLine = headers != null
                ? RequestAuthHeaders.resolveBearerHeaderLine(
                        headers.getHeaderString(HttpHeaders.AUTHORIZATION),
                        headers.getHeaderString(RequestAuthHeaders.BAGGAGI_AUTHORIZATION))
                : null;

        if (bearerLine == null) {
            return AuthResult.unauthenticated();
        }

        String token;
        try {
            token = JwtAuthSupport.extractBearer(bearerLine);
        } catch (AuthTokenException e) {
            return AuthResult.unauthenticated();
        }

        try {
            String userIdStr = tokenService.validateToken(token);
            UUID userId = UUID.fromString(userIdStr);
            var user = userRepository.findById(userId);
            if (user == null) {
                return AuthResult.unauthenticated();
            }
            String displayName = user.getFullName() != null ? user.getFullName() : user.getUsername();
            return new AuthResult(true, userIdStr, displayName);
        } catch (Exception e) {
            return AuthResult.unauthenticated();
        }
    }

    // =========================================================================
    // Mappers — entidade → response DTO
    // =========================================================================

    private static PostResponse toResponse(Post post) {
        PostResponse r = new PostResponse();
        r.setPostId(post.getPostId());
        r.setAuthorId(post.getAuthorId());
        r.setAuthorName(post.getAuthorName());
        r.setText(post.getText());
        r.setTags(post.getTags());
        r.setLocation(post.getLocation());
        r.setPostedAt(post.getPostedAt());
        r.setUpdatedAt(post.getUpdatedAt());
        r.setImageUrl(post.getImageUrl());
        r.setLikeCount(post.getLikeCount());
        r.setCommentCount(post.getCommentCount());
        return r;
    }

    private static PageResponse toPageResponse(
            org.example.infrastructure.repository.PostDynamoRepository.PagedResult result) {
        List<PostResponse> items = result.items().stream()
                .map(PostController::toResponse)
                .collect(Collectors.toList());
        return new PageResponse(items, result.nextToken());
    }

    // =========================================================================
    // Response helpers
    // =========================================================================

    private static Response badRequest(String code, String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(errorBody(code, message)).build();
    }

    private static Response unauthorized(String message) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(errorBody("UNAUTHORIZED", message)).build();
    }

    private static Response forbidden(String message) {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(errorBody("FORBIDDEN", message)).build();
    }

    private static Response notFound(String message) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(errorBody("NOT_FOUND", message)).build();
    }

    private static Response internalError(String message) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(errorBody("INTERNAL_ERROR", message)).build();
    }

    private static Map<String, String> errorBody(String code, String message) {
        return Map.of("code", code, "message", message != null ? message : "");
    }

    // =========================================================================
    // DTOs internos
    // =========================================================================

    /** Corpo da requisição de criação de postagem. */
    @Data
    public static class CreatePostRequest {
        /** Texto principal da postagem. Obrigatório. */
        private String text;
        /** Nome do autor (opcional — usa displayName do token se ausente). */
        private String authorName;
        /** Lista de tags. Opcional. */
        private List<String> tags;
        /** Localização livre (ex.: "Rio de Janeiro, RJ"). Opcional. */
        private String location;
        /** Horário de postagem (ISO-8601). Opcional — usa horário do servidor se ausente. */
        private String postedAt;
        /** URL da imagem vinculada. Opcional. */
        private String imageUrl;
    }

    /** Corpo da requisição de edição de postagem. */
    @Data
    public static class UpdatePostRequest {
        /** Novo texto da postagem. Obrigatório. */
        private String text;
        /** Novas tags. Opcional. */
        private List<String> tags;
        /** Nova localização. Opcional. */
        private String location;
        /** Nova URL de imagem. Opcional. */
        private String imageUrl;
    }

    /** Corpo da requisição de comentário. */
    @Data
    public static class CommentRequest {
        /** Texto do comentário. Obrigatório. */
        private String text;
    }

    /** Resposta de postagem — enriquecida com `likedByMe`. */
    @Data
    public static class PostResponse {
        private String postId;
        private String authorId;
        private String authorName;
        private String text;
        private List<String> tags;
        private String location;
        private String postedAt;
        private String updatedAt;
        private String imageUrl;
        private long likeCount;
        private long commentCount;
        /** Apenas preenchido quando o usuário autenticado faz GET de uma postagem específica. */
        private boolean likedByMe;
    }

    /** Resultado da resolução de autenticação — evita null checks no código. */
    private record AuthResult(boolean authenticated, String userId, String displayName) {
        static AuthResult unauthenticated() {
            return new AuthResult(false, null, null);
        }
    }

    /**
     * Envelope de resposta paginada para listagem de posts.
     *
     * <p>Exemplo de uso no frontend (infinite scroll):
     * <pre>
     * 1ª chamada: GET /api/v1/posts/user/{id}?limit=20
     * → { "items": [...], "nextToken": "eyJwb3N0SWQiO..." }
     *
     * 2ª chamada: GET /api/v1/posts/user/{id}?limit=20&nextToken=eyJwb3N0SWQiO...
     * → { "items": [...], "nextToken": null }   ← fim dos resultados
     * </pre>
     *
     * Para o feed de múltiplos autores, use {@code ?before=<nextToken>} em vez de {@code ?nextToken}.
     *
     * @param items     Posts desta página, ordenados do mais recente ao mais antigo.
     * @param nextToken Cursor para a próxima página. {@code null} = não há mais resultados.
     */
    public record PageResponse(List<PostResponse> items, String nextToken) {}
}
