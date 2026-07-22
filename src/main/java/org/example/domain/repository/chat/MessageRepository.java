package org.example.domain.repository.chat;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.chat.Message;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class MessageRepository implements PanacheRepositoryBase<Message, UUID> {

    public List<Message> findPage(UUID conversationId, Instant beforeCreatedAt, UUID beforeId, int limit) {
        if (beforeCreatedAt != null && beforeId != null) {
            return getEntityManager()
                    .createQuery(
                            "SELECT m FROM Message m "
                                    + "JOIN FETCH m.sender "
                                    + "WHERE m.conversation.id = :cid "
                                    + "AND (m.createdAt < :beforeAt OR (m.createdAt = :beforeAt AND m.id < :beforeId)) "
                                    + "ORDER BY m.createdAt DESC, m.id DESC",
                            Message.class)
                    .setParameter("cid", conversationId)
                    .setParameter("beforeAt", beforeCreatedAt)
                    .setParameter("beforeId", beforeId)
                    .setMaxResults(limit)
                    .getResultList();
        }
        return getEntityManager()
                .createQuery(
                        "SELECT m FROM Message m "
                                + "JOIN FETCH m.sender "
                                + "WHERE m.conversation.id = :cid "
                                + "ORDER BY m.createdAt DESC, m.id DESC",
                        Message.class)
                .setParameter("cid", conversationId)
                .setMaxResults(limit)
                .getResultList();
    }

    public Optional<Message> findInConversation(UUID messageId, UUID conversationId) {
        return find("id = ?1 and conversation.id = ?2", messageId, conversationId).firstResultOptional();
    }
}
