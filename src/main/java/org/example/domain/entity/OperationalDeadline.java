package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.example.domain.enums.OperationalAlertLevel;
import org.example.domain.enums.OperationalDeadlineType;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "operational_deadlines")
public class OperationalDeadline extends PanacheEntityBase {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private OperationalService service;

    @Enumerated(EnumType.STRING)
    @Column(name = "deadline_type", nullable = false, length = 64)
    private OperationalDeadlineType deadlineType;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_level", nullable = false, length = 16)
    @Builder.Default
    private OperationalAlertLevel alertLevel = OperationalAlertLevel.INFO;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
