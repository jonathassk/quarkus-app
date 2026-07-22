package org.example.domain.repository.chat;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.chat.ConversationParticipant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ConversationParticipantRepository implements PanacheRepositoryBase<ConversationParticipant, UUID> {

    public Optional<ConversationParticipant> findActive(UUID conversationId, UUID userId) {
        return find(
                        "conversation.id = ?1 and user.id = ?2 and leftAt is null",
                        conversationId,
                        userId)
                .firstResultOptional();
    }

    public List<ConversationParticipant> findActiveByConversation(UUID conversationId) {
        return list("conversation.id = ?1 and leftAt is null", conversationId);
    }

    public List<ConversationParticipant> findActiveByUser(UUID userId, int limit) {
        return getEntityManager()
                .createQuery(
                        "SELECT cp FROM ConversationParticipant cp "
                                + "JOIN FETCH cp.conversation c "
                                + "WHERE cp.user.id = :uid AND cp.leftAt IS NULL "
                                + "ORDER BY c.lastMessageAt DESC NULLS LAST, c.createdAt DESC",
                        ConversationParticipant.class)
                .setParameter("uid", userId)
                .setMaxResults(limit)
                .getResultList();
    }

    public void incrementUnreadForOthers(UUID conversationId, UUID senderId) {
        getEntityManager()
                .createQuery(
                        "UPDATE ConversationParticipant cp SET cp.unreadCount = cp.unreadCount + 1 "
                                + "WHERE cp.conversation.id = :cid AND cp.user.id <> :sid AND cp.leftAt IS NULL")
                .setParameter("cid", conversationId)
                .setParameter("sid", senderId)
                .executeUpdate();
    }

    public void resetUnread(UUID conversationId, UUID userId) {
        getEntityManager()
                .createQuery(
                        "UPDATE ConversationParticipant cp SET cp.unreadCount = 0, cp.lastReadAt = CURRENT_TIMESTAMP "
                                + "WHERE cp.conversation.id = :cid AND cp.user.id = :uid AND cp.leftAt IS NULL")
                .setParameter("cid", conversationId)
                .setParameter("uid", userId)
                .executeUpdate();
    }
}
