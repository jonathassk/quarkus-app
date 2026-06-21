package org.example.application.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.example.domain.entity.Post;
import org.example.domain.entity.PostComment;
import org.example.infrastructure.repository.PostDynamoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orquestra operações de postagens, likes e comentários.
 */
@Slf4j
@ApplicationScoped
public class PostService {

    private final PostDynamoRepository postRepo;

    @Inject
    public PostService(PostDynamoRepository postRepo) {
        this.postRepo = postRepo;
    }

    // =========================================================================
    // Postagem — CRUD
    // =========================================================================

    public Post createPost(CreatePostCommand cmd) {
        String now = Instant.now().toString();
        Post post = Post.builder()
                .postId(UUID.randomUUID().toString())
                .authorId(cmd.authorId())
                .authorName(cmd.authorName())
                .text(cmd.text())
                .tags(cmd.tags())
                .location(cmd.location())
                .postedAt(cmd.postedAt() != null ? cmd.postedAt() : now)
                .updatedAt(now)
                .imageUrl(cmd.imageUrl())
                .likeCount(0)
                .commentCount(0)
                .build();

        postRepo.savePost(post);
        log.info("Post created postId={} authorId={}", post.getPostId(), post.getAuthorId());
        return post;
    }

    public Post updatePost(String postId, String requestingUserId, UpdatePostCommand cmd) {
        Post existing = findPostOrThrow(postId);
        assertOwner(existing, requestingUserId);

        existing.setText(cmd.text());
        existing.setTags(cmd.tags());
        existing.setLocation(cmd.location());
        existing.setImageUrl(cmd.imageUrl());
        existing.setAuthorName(cmd.authorName());
        existing.setUpdatedAt(Instant.now().toString());

        postRepo.updatePost(existing);
        log.info("Post updated postId={} by userId={}", postId, requestingUserId);
        return existing;
    }

    public void deletePost(String postId, String requestingUserId) {
        Post existing = findPostOrThrow(postId);
        assertOwner(existing, requestingUserId);
        postRepo.deletePost(postId);
        log.info("Post deleted postId={} by userId={}", postId, requestingUserId);
    }

    public Optional<Post> findById(String postId) {
        return postRepo.findPostById(postId);
    }

    // =========================================================================
    // Listagem paginada por autor / feed
    // =========================================================================

    /**
     * Lista posts de um único autor, paginados (mais recente primeiro).
     *
     * @param authorId  ID do autor.
     * @param limit     Tamanho da página (1–50).
     * @param nextToken Cursor da página anterior (Base64-JSON), ou {@code null} para a 1ª página.
     * @return          Página de posts + cursor para a próxima.
     */
    public PostDynamoRepository.PagedResult listPostsByAuthor(
            String authorId, int limit, String nextToken) {
        return postRepo.listByAuthor(authorId, limit, nextToken);
    }

    /**
     * Feed de múltiplos autores (seguidos), paginado por cursor temporal.
     *
     * @param authorIds IDs dos autores a incluir no feed.
     * @param limit     Tamanho da página (1–50).
     * @param before    Cursor ISO-8601 — retorna posts anteriores a este timestamp.
     *                  Passe o {@code nextToken} da última resposta como {@code before}.
     *                  {@code null} para a primeira página.
     * @return          Página de posts + cursor para a próxima (ou {@code null} se não há mais).
     */
    public PostDynamoRepository.PagedResult listFeed(
            List<String> authorIds, int limit, String before) {
        return postRepo.listByAuthors(authorIds, limit, before);
    }

    // =========================================================================
    // Likes
    // =========================================================================

    /**
     * Alterna o like de um usuário em uma postagem.
     *
     * @return {@code true} se o like foi adicionado, {@code false} se foi removido.
     */
    public boolean toggleLike(String postId, String userId) {
        findPostOrThrow(postId); // valida existência
        return postRepo.toggleLike(postId, userId);
    }

    public boolean hasLiked(String postId, String userId) {
        return postRepo.hasLiked(postId, userId);
    }

    // =========================================================================
    // Comentários
    // =========================================================================

    public PostComment addComment(String postId, String authorId, String authorName, String text) {
        findPostOrThrow(postId); // valida existência
        String now = Instant.now().toString();
        PostComment comment = PostComment.builder()
                .commentId(UUID.randomUUID().toString())
                .postId(postId)
                .authorId(authorId)
                .authorName(authorName)
                .text(text)
                .createdAt(now)
                .build();
        postRepo.addComment(comment);
        log.info("Comment added postId={} commentId={} authorId={}", postId, comment.getCommentId(), authorId);
        return comment;
    }

    public void deleteComment(String postId, String commentId, String requestingUserId) {
        // Qualquer usuário autenticado pode apagar seu próprio comentário;
        // sem auditoria de ownership aqui — pode ser expandido via query do comentário.
        postRepo.deleteComment(postId, commentId);
        log.info("Comment deleted postId={} commentId={} by userId={}", postId, commentId, requestingUserId);
    }

    public List<PostComment> listComments(String postId) {
        findPostOrThrow(postId);
        return postRepo.listComments(postId);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Post findPostOrThrow(String postId) {
        return postRepo.findPostById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found: " + postId));
    }

    private static void assertOwner(Post post, String requestingUserId) {
        if (!post.getAuthorId().equals(requestingUserId)) {
            throw new PostForbiddenException("User " + requestingUserId + " is not the author of post " + post.getPostId());
        }
    }

    // =========================================================================
    // Commands (records imutáveis passados pelo controller)
    // =========================================================================

    public record CreatePostCommand(
            String authorId,
            String authorName,
            String text,
            List<String> tags,
            String location,
            String postedAt,
            String imageUrl
    ) {}

    public record UpdatePostCommand(
            String authorName,
            String text,
            List<String> tags,
            String location,
            String imageUrl
    ) {}

    // =========================================================================
    // Exceptions
    // =========================================================================

    public static class PostNotFoundException extends RuntimeException {
        public PostNotFoundException(String message) { super(message); }
    }

    public static class PostForbiddenException extends RuntimeException {
        public PostForbiddenException(String message) { super(message); }
    }
}
