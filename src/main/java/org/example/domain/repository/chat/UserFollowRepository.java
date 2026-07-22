package org.example.domain.repository.chat;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.chat.UserFollow;

@ApplicationScoped
public class UserFollowRepository implements PanacheRepositoryBase<UserFollow, UserFollow.UserFollowId> {

    public boolean isFollowing(UUID followerId, UUID followingId) {
        return count(
                        "id.followerId = ?1 and id.followingId = ?2",
                        followerId,
                        followingId)
                > 0;
    }
}
