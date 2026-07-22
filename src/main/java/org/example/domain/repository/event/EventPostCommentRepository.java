package org.example.domain.repository.event;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.event.EventPostComment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class EventPostCommentRepository implements PanacheRepositoryBase<EventPostComment, UUID> {

    public List<EventPostComment> findByPostId(UUID postId) {
        return list("post.id = ?1 and deletedAt is null order by createdAt asc", postId);
    }

    public Optional<EventPostComment> findActiveById(UUID commentId) {
        return find("id = ?1 and deletedAt is null", commentId).firstResultOptional();
    }
}
