package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.example.utils.StringListConverter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_travel_preferences")
public class UserTravelPreferences extends PanacheEntityBase {

    @Id
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "BRL";

    @Column(name = "unit_system", nullable = false, length = 16)
    @Builder.Default
    private String unitSystem = "metric";

    @Column(name = "seat_preference", nullable = false, length = 16)
    @Builder.Default
    private String seatPreference = "none";

    @Column(name = "accommodation_preference", nullable = false, length = 16)
    @Builder.Default
    private String accommodationPreference = "hotel";

    @Convert(converter = StringListConverter.class)
    @Column(name = "dietary_restrictions", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private List<String> dietaryRestrictions = new ArrayList<>();

    @Column(name = "base_airport", nullable = false, length = 3)
    @Builder.Default
    private String baseAirport = "";

    @Column(name = "cloud_backup_enabled", nullable = false)
    @Builder.Default
    private boolean cloudBackupEnabled = false;

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
