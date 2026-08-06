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

    // ── Passageiros / formulários ──────────────────────────────────────────────
    PASSENGER_CREATED,
    PASSENGER_UPDATED,
    PASSENGER_DELETED,
    PASSENGER_INVITED,
    PASSENGER_FORM_SUBMITTED,
    PASSENGER_MARKED_REVIEWED,
    PASSENGER_CORRECTION_REQUESTED,
    PASSENGER_CORRECTION_RESOLVED,
    PASSENGER_PROFILE_CONSENT,

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
    PROPOSAL_PAYMENT_PENDING,
    PROPOSAL_PAYMENT_RECEIVED,
    PROPOSAL_CONFIRMED,

    // ── CRM / atribuição ──────────────────────────────────────────────────────
    TRIP_ASSIGNED,
    CLIENT_LINKED,

    // ── Operação / reservas ───────────────────────────────────────────────────
    OPS_SERVICES_MATERIALIZED,
    OPS_SERVICE_STATUS_CHANGED,
    OPS_SERVICE_CONFIRMED,
    OPS_SERVICE_CANCELLED,
    OPS_SERVICE_PUBLISHED,
    OPS_DEADLINE_COMPLETED,
    OPS_DOCUMENT_LINKED,
    OPS_DOCUMENT_STATUS_CHANGED,
    OPS_PASSENGERS_LINKED,
    OPS_CHANGE_REQUESTED,
    OPS_CHANGE_UPDATED,
    OPS_SUPPLIER_ASSIGNED,
}
