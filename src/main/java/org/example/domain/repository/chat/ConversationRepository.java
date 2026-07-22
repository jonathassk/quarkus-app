package org.example.domain.repository.chat;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.chat.Conversation;
import org.example.domain.enums.ConversationType;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ConversationRepository implements PanacheRepositoryBase<Conversation, UUID> {

    public Optional<Conversation> findByTypeAndRefId(ConversationType type, UUID refId) {
        return find("type = ?1 and refId = ?2", type, refId).firstResultOptional();
    }

    public Optional<Conversation> findByTypeAndRefUuid(ConversationType type, UUID refUuid) {
        return find("type = ?1 and refUuid = ?2", type, refUuid).firstResultOptional();
    }
}
