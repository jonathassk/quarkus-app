package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.example.domain.enums.DocumentStatus;
import org.example.domain.enums.DocumentVisibility;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "trip_documents")
public class TripDocument extends PanacheEntityBase {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "s3_key", nullable = false, length = 512)
    private String s3Key;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private DocumentVisibility visibility = DocumentVisibility.CLIENT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id")
    private Activity activity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "segment_id")
    private TripSegment segment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
