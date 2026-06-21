package org.example.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa um comentário em uma postagem.
 *
 * <p>Armazenado na tabela DynamoDB {@code posts_network} com:
 * <ul>
 *   <li>Partition Key: {@code postId}</li>
 *   <li>Sort Key: {@code COMMENT#<commentId>} (prefixo de entidade)</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostComment {

    /** UUID do comentário. */
    private String commentId;

    /** UUID da postagem pai. */
    private String postId;

    /** ID do autor do comentário. */
    private String authorId;

    /** Nome de exibição do autor do comentário. */
    private String authorName;

    /** Texto do comentário. */
    private String text;

    /** ISO-8601 de criação. */
    private String createdAt;
}
