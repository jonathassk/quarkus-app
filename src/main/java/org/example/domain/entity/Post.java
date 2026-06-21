package org.example.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Representa uma postagem na rede social.
 *
 * <p>Mapeada para a tabela DynamoDB {@code posts_network} com:
 * <ul>
 *   <li>Partition Key: {@code postId} (String / UUID)</li>
 *   <li>Sort Key: {@code authorId} (String)</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Post {

    /** UUID da postagem — partition key no DynamoDB. */
    private String postId;

    /** ID do autor (userId interno ou authUserId Neon Auth). */
    private String authorId;

    /** Nome de exibição do autor no momento da postagem. */
    private String authorName;

    /** Corpo/texto da postagem. */
    private String text;

    /** Lista de tags associadas. */
    private List<String> tags;

    /** Localização geográfica em formato livre (ex.: "São Paulo, SP"). */
    private String location;

    /** ISO-8601 do horário de criação (ex.: 2024-06-19T22:00:00Z). */
    private String postedAt;

    /** ISO-8601 da última atualização. */
    private String updatedAt;

    /** URL de uma imagem vinculada à postagem. */
    private String imageUrl;

    /** Número de likes (desnormalizado para leitura rápida). */
    private long likeCount;

    /** Número de comentários (desnormalizado para leitura rápida). */
    private long commentCount;
}
