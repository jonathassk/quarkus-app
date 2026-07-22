package org.example.domain.entity.chat;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.example.domain.entity.User;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "conversation_participants",
        uniqueConstraints = @UniqueConstraint(name = "uq_conversation_participant", columnNames = {"conversation_id", "user_id"})
)
public class ConversationParticipant extends PanacheEntityBase {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @Column(name = "last_read_message_id", columnDefinition = "uuid")
    private UUID lastReadMessageId;

    @Column(name = "unread_count", nullable = false)
    @Builder.Default
    private int unreadCount = 0;

    @PrePersist
    protected void onCreate() {
        if (joinedAt == null) {
            joinedAt = Instant.now();
        }
    }

    public boolean isActive() {
        return leftAt == null;
    }
}
