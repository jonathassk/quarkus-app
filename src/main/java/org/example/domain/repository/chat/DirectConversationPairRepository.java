package org.example.domain.repository.chat;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.chat.DirectConversationPair;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class DirectConversationPairRepository implements PanacheRepositoryBase<DirectConversationPair, UUID> {

    public Optional<DirectConversationPair> findByUsers(UUID userA, UUID userB) {
        UUID low = userA.compareTo(userB) <= 0 ? userA : userB;
        UUID high = userA.compareTo(userB) <= 0 ? userB : userA;
        return find("userLow.id = ?1 and userHigh.id = ?2", low, high).firstResultOptional();
    }
}
