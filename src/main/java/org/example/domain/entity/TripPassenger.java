package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.example.domain.enums.PassengerFormStatus;
import org.example.domain.enums.PassengerType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "trip_passengers")
public class TripPassenger extends PanacheEntityBase {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_client_id")
    private AgencyClient sourceClient;

    @Column(name = "full_name", length = 255)
    private String displayName;

    @Column(length = 255)
    private String email;

    @Column(length = 64)
    private String phone;

    @Column(name = "document_type", length = 32)
    private String documentType;

    @Column(name = "document_number", length = 64)
    private String documentNumber;

    @Column(name = "document_expires_at")
    private LocalDate documentExpiresAt;

    @Column(length = 128)
    private String nationality;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "passenger_type", nullable = false, length = 16)
    @Builder.Default
    private PassengerType passengerType = PassengerType.ADULT;

    @Column(name = "is_lead", nullable = false)
    @Builder.Default
    private boolean primaryContact = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "doc_checklist_json", columnDefinition = "jsonb")
    private String docChecklistJson;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "form_status", nullable = false, length = 32)
    @Builder.Default
    private PassengerFormStatus formStatus = PassengerFormStatus.NOT_REQUESTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guardian_passenger_id")
    private TripPassenger guardian;

    @Column(length = 64)
    private String whatsapp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "form_payload", columnDefinition = "jsonb")
    private String formPayload;

    @Column(name = "invite_token", length = 80, unique = true)
    private String inviteToken;

    @Column(name = "invite_sent_at")
    private Instant inviteSentAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (passengerType == null) {
            passengerType = PassengerType.ADULT;
        }
        if (formStatus == null) {
            formStatus = PassengerFormStatus.NOT_REQUESTED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
