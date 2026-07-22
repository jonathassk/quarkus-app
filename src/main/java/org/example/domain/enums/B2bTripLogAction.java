package org.example.domain.enums;

/**
 * Tipos de operação registrados no audit trail B2B ({@code b2b_trip_logs}).
 *
 * <p>Convenção de nomenclatura: {@code ENTITY_VERB}
 * – entidade em letras maiúsculas, verbo no passado em inglês.
 */
public enum B2bTripLogAction {

    // ── Viagem (Trip) ────────────────────────────────────────────────────────
    TRIP_CREATED,
    TRIP_UPDATED,
    TRIP_DELETED,
    TRIP_STATUS_CHANGED,

    // ── Segmento (TripSegment) ────────────────────────────────────────────────
    SEGMENT_CREATED,
    SEGMENT_UPDATED,
    SEGMENT_DELETED,

    // ── Atividade (Activity) ──────────────────────────────────────────────────
    ACTIVITY_CREATED,
    ACTIVITY_UPDATED,
    ACTIVITY_DELETED,

    // ── Refeição (Meal) ───────────────────────────────────────────────────────
    MEAL_CREATED,
    MEAL_UPDATED,
    MEAL_DELETED,

    // ── Checklist ─────────────────────────────────────────────────────────────
    CHECKLIST_ITEM_CREATED,
    CHECKLIST_ITEM_UPDATED,
    CHECKLIST_ITEM_DELETED,

    // ── Documentos ────────────────────────────────────────────────────────────
    DOCUMENT_UPLOADED,
    DOCUMENT_DELETED,

    // ── Membros da viagem (TripUser) ──────────────────────────────────────────
    MEMBER_ADDED,
    MEMBER_REMOVED,
    MEMBER_PERMISSION_CHANGED,

    // ── Operações financeiras ─────────────────────────────────────────────────
    BUDGET_UPDATED,

    // ── Proposta interativa ───────────────────────────────────────────────────
    PROPOSAL_SENT,
    PROPOSAL_APPROVED,
    PROPOSAL_REJECTED,
    PROPOSAL_PRICING_UPDATED,
    PROPOSAL_TIERS_UPDATED,
}
