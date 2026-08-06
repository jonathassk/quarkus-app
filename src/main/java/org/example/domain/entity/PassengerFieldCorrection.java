package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.example.domain.enums.PassengerCorrectionStatus;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "passenger_field_corrections")
public class PassengerFieldCorrection extends PanacheEntityBase {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_passenger_id", nullable = false)
    private TripPassenger passenger;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "field_name", nullable = false, length = 64)
    private String fieldName;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "expected_value", columnDefinition = "TEXT")
    private String expectedValue;

    @Column(name = "corrected_value", columnDefinition = "TEXT")
    private String correctedValue;

    @Column(name = "agent_note", columnDefinition = "TEXT")
    private String agentNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private PassengerCorrectionStatus status = PassengerCorrectionStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id")
    private User requestedBy;

    @Column(name = "resolved_by_label", length = 255)
    private String resolvedByLabel;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (requestedAt == null) {
            requestedAt = now;
        }
        if (status == null) {
            status = PassengerCorrectionStatus.OPEN;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
