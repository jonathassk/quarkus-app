package org.example.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa o like de um usuário em uma postagem.
 *
 * <p>Armazenado na tabela DynamoDB {@code posts_network} com:
 * <ul>
 *   <li>Partition Key: {@code postId}</li>
 *   <li>Sort Key: {@code LIKE#<userId>} (prefixo de entidade — garante idempotência)</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostLike {

    /** UUID da postagem. */
    private String postId;

    /** ID do usuário que curtiu. */
    private String userId;

    /** ISO-8601 do momento do like. */
    private String likedAt;
}
