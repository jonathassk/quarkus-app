package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.Notification;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class NotificationRepository implements PanacheRepositoryBase<Notification, UUID> {

    public List<Notification> findPage(UUID userId, boolean unreadOnly, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        if (unreadOnly) {
            return find(
                            "user.id = ?1 AND readAt IS NULL ORDER BY createdAt DESC",
                            userId)
                    .page(safePage, safeSize)
                    .list();
        }
        return find("user.id = ?1 ORDER BY createdAt DESC", userId)
                .page(safePage, safeSize)
                .list();
    }

    public long countByUser(UUID userId, boolean unreadOnly) {
        if (unreadOnly) {
            return count("user.id = ?1 AND readAt IS NULL", userId);
        }
        return count("user.id", userId);
    }

    public long countUnread(UUID userId) {
        return count("user.id = ?1 AND readAt IS NULL", userId);
    }

    public List<Notification> findOwnedByIds(UUID userId, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return list("user.id = ?1 AND id in ?2", userId, ids);
    }

    public int markAllRead(UUID userId) {
        return getEntityManager()
                .createQuery(
                        "UPDATE Notification n SET n.readAt = CURRENT_TIMESTAMP "
                                + "WHERE n.user.id = :uid AND n.readAt IS NULL")
                .setParameter("uid", userId)
                .executeUpdate();
    }

    public boolean existsUnreadOrRecent(
            UUID userId, String entityType, UUID entityId, org.example.domain.enums.NotificationKind kind) {
        return count(
                        "user.id = ?1 AND entityType = ?2 AND entityId = ?3 AND kind = ?4",
                        userId,
                        entityType,
                        entityId,
                        kind)
                > 0;
    }
}
