package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.example.domain.enums.OpportunityNextActionType;
import org.example.domain.enums.OpportunityTaskCompletionOutcome;
import org.example.domain.enums.OpportunityTaskOrigin;
import org.example.domain.enums.OpportunityTaskPriority;
import org.example.domain.enums.OpportunityTaskStatus;
import org.example.domain.enums.OpportunityTaskType;
import org.example.domain.enums.OpportunityTaskWaitingOn;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "agency_opportunity_tasks")
public class AgencyOpportunityTask extends PanacheEntityBase {

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

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private OpportunityTaskStatus status = OpportunityTaskStatus.OPEN;

    @Column(name = "due_at")
    private Instant dueAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_user_id")
    private User assignee;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 32)
    @Builder.Default
    private OpportunityTaskType taskType = OpportunityTaskType.COMMERCIAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_kind", length = 64)
    private OpportunityNextActionType actionKind;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "waiting_on", length = 32)
    private OpportunityTaskWaitingOn waitingOn;

    @Column(name = "is_next_action", nullable = false)
    @Builder.Default
    private boolean nextAction = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private OpportunityTaskOrigin origin = OpportunityTaskOrigin.MANUAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by_user_id")
    private User completedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_outcome", length = 64)
    private OpportunityTaskCompletionOutcome completionOutcome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private OpportunityTaskPriority priority = OpportunityTaskPriority.NORMAL;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
