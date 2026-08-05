package org.example.domain.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Status do funil B2B (independente do {@link TripStatus} calendário).
 *
 * <p>Colunas ativas do kanban: {@link #DRAFT}, {@link #QUOTING}, {@link #SENT},
 * {@link #NEGOTIATING}, {@link #PENDING_PAYMENT}, {@link #CONFIRMED}, {@link #IN_TRIP}.
 * Saídas laterais (histórico): {@link #REJECTED}, {@link #LOST}, {@link #CANCELLED},
 * {@link #COMPLETED}.
 */
public enum ProposalStatus {
    /** Novo lead / rascunho. */
    DRAFT,
    /** Montando cotação e roteiro. */
    QUOTING,
    /** Proposta enviada ao cliente. */
    SENT,
    /** Em negociação (só se {@code allowNegotiation}). */
    NEGOTIATING,
    /**
     * Legado: aceite sem pagamento. Novos fluxos usam {@link #CONFIRMED}.
     * Mantido para compatibilidade de leitura.
     */
    APPROVED,
    /** Aceite com valor; aguardando pagamento. */
    PENDING_PAYMENT,
    /** Venda fechada — operação/reservas. */
    CONFIRMED,
    /** Cliente em viagem. */
    IN_TRIP,
    REJECTED,
    LOST,
    CANCELLED,
    /** Viagem concluída — arquivo/compliance. */
    COMPLETED;

    public static final Set<ProposalStatus> ACTIVE_PIPELINE = EnumSet.of(
            DRAFT, QUOTING, SENT, NEGOTIATING, PENDING_PAYMENT, CONFIRMED, IN_TRIP, APPROVED);

    public static final Set<ProposalStatus> ARCHIVE = EnumSet.of(
            REJECTED, LOST, CANCELLED, COMPLETED);

    public boolean isActivePipeline() {
        return ACTIVE_PIPELINE.contains(this);
    }

    public boolean isArchive() {
        return ARCHIVE.contains(this);
    }

    public static ProposalStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return DRAFT;
        }
        for (ProposalStatus s : values()) {
            if (s.name().equalsIgnoreCase(value.trim())) {
                return s;
            }
        }
        throw new IllegalArgumentException("proposal_status inválido: " + value);
    }
}
