package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.example.domain.enums.B2bTripLogAction;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de auditoria operacional para viagens B2B.
 *
 * <p>Toda operação mutável em uma viagem de agência (edição, exclusão de segmento,
 * alteração de horário, mudança de status, etc.) gera uma entrada nesta tabela.
 * O {@code agencyOwnerId} pode consultar o histórico completo de uma viagem para
 * auditar ações de seus consultores.
 *
 * <p>Campos de snapshot ({@code previousSnapshot}, {@code newSnapshot}) armazenam
 * um resumo JSON do estado antes e depois da operação — não o payload completo,
 * apenas os campos relevantes para auditoria (ex.: {@code {"startTime": "03:00", "endTime": "04:00"}}).
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "b2b_trip_logs",
    indexes = {
        @Index(name = "idx_b2b_trip_logs_trip",   columnList = "trip_id"),
        @Index(name = "idx_b2b_trip_logs_actor",  columnList = "actor_user_id"),
        @Index(name = "idx_b2b_trip_logs_agency", columnList = "agency_id")
    }
)
public class B2bTripLog extends PanacheEntityBase {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid")
    public UUID id;

    /** Agência à qual a viagem pertence. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    /** Viagem auditada. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    /** Usuário que executou a operação (consultor ou dono da agência). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id", nullable = false)
    private User actorUser;

    /** Tipo de operação realizada. */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 60)
    private B2bTripLogAction action;

    /**
     * Entidade-alvo da operação (ex.: "SEGMENT", "ACTIVITY", "MEAL", "TRIP",
     * "CHECKLIST_ITEM", "DOCUMENT", "MEMBER").
     */
    @Column(name = "entity_type", length = 60)
    private String entityType;

    /** ID da entidade-alvo (segmento, atividade, refeição, etc.), se aplicável. */
    @Column(name = "entity_id", columnDefinition = "uuid")
    private UUID entityId;

    /**
     * Resumo JSON do estado anterior à operação.
     * Ex.: {@code {"name": "Transfer para o aeroporto", "startTime": "03:00"}}
     */
    @Column(name = "previous_snapshot", columnDefinition = "TEXT")
    private String previousSnapshot;

    /**
     * Resumo JSON do estado posterior à operação.
     * Ex.: {@code {"name": "Transfer para o aeroporto", "startTime": "05:30"}}
     */
    @Column(name = "new_snapshot", columnDefinition = "TEXT")
    private String newSnapshot;

    /** Descrição legível gerada automaticamente pelo sistema para exibição no audit trail. */
    @Column(name = "description", length = 500)
    private String description;

    /** IP de origem da requisição, se disponível (para rastreabilidade extra). */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
