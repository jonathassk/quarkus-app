package org.example.domain.repository.event;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.event.EventPost;
import org.example.domain.entity.event.EventPostPollVote;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class EventPostRepository implements PanacheRepositoryBase<EventPost, UUID> {

    public List<EventPost> findByEventId(UUID eventId, int limit, Instant cursorPostedAt, UUID cursorId) {
        StringBuilder jpql =
                new StringBuilder(
                        "SELECT p FROM EventPost p WHERE p.event.id = :eventId AND p.deletedAt IS NULL");
        if (cursorPostedAt != null && cursorId != null) {
            jpql.append(
                    " AND (p.postedAt < :cursorPostedAt OR (p.postedAt = :cursorPostedAt AND p.id < :cursorId))");
        }
        jpql.append(" ORDER BY p.postedAt DESC, p.id DESC");

        var query = getEntityManager().createQuery(jpql.toString(), EventPost.class);
        query.setParameter("eventId", eventId);
        if (cursorPostedAt != null && cursorId != null) {
            query.setParameter("cursorPostedAt", cursorPostedAt);
            query.setParameter("cursorId", cursorId);
        }
        query.setMaxResults(limit);
        return query.getResultList();
    }

    public Optional<EventPost> findActiveById(UUID postId) {
        return find("id = ?1 and deletedAt is null", postId).firstResultOptional();
    }

    public long countLikes(UUID postId) {
        return getEntityManager()
                .createQuery("SELECT COUNT(l) FROM EventPostLike l WHERE l.postId = :postId", Long.class)
                .setParameter("postId", postId)
                .getSingleResult();
    }

    public long countComments(UUID postId) {
        return getEntityManager()
                .createQuery(
                        "SELECT COUNT(c) FROM EventPostComment c WHERE c.post.id = :postId AND c.deletedAt IS NULL",
                        Long.class)
                .setParameter("postId", postId)
                .getSingleResult();
    }

    public boolean isLikedByUser(UUID postId, UUID userId) {
        Long count =
                getEntityManager()
                        .createQuery(
                                "SELECT COUNT(l) FROM EventPostLike l WHERE l.postId = :postId AND l.userId = :userId",
                                Long.class)
                        .setParameter("postId", postId)
                        .setParameter("userId", userId)
                        .getSingleResult();
        return count != null && count > 0;
    }

    public Optional<EventPostPollVote> findPollVote(UUID postId, UUID userId) {
        return getEntityManager()
                .createQuery(
                        "SELECT v FROM EventPostPollVote v WHERE v.postId = :postId AND v.userId = :userId",
                        EventPostPollVote.class)
                .setParameter("postId", postId)
                .setParameter("userId", userId)
                .getResultStream()
                .findFirst();
    }

    /** Returns optionIndex -> vote count for a post. */
    public Map<Integer, Long> countPollVotesByOption(UUID postId) {
        List<Object[]> rows =
                getEntityManager()
                        .createQuery(
                                "SELECT v.optionIndex, COUNT(v) FROM EventPostPollVote v"
                                        + " WHERE v.postId = :postId GROUP BY v.optionIndex",
                                Object[].class)
                        .setParameter("postId", postId)
                        .getResultList();
        Map<Integer, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((Integer) row[0], (Long) row[1]);
        }
        return counts;
    }
}
