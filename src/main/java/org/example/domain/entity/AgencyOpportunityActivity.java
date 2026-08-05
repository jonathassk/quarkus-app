package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.example.domain.enums.OpportunityActivityType;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "agency_opportunity_activities")
public class AgencyOpportunityActivity extends PanacheEntityBase {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opportunity_id", nullable = false)
    private AgencyOpportunity opportunity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @Column(name = "actor_label", length = 255)
    private String actorLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 64)
    private OpportunityActivityType activityType;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
