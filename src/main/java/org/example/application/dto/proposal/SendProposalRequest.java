package org.example.application.dto.proposal;

import lombok.*;

/**
 * Corpo opcional de {@code POST /trips/{tripId}/proposal/send}.
 *
 * <p>Quando omitido, o envio reutiliza o contato salvo na viagem. É obrigatório
 * informar o {@code clientEmail} no primeiro envio.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendProposalRequest {
    private String clientEmail;
    private String clientName;
    /** Dias de validade a partir do envio (default 7). Ignorado se {@code proposalExpiresAt} for informado. */
    private Integer expiresInDays;
    /** Validade absoluta (opcional). */
    private java.time.Instant proposalExpiresAt;
    /**
     * Se true, o agente pode mover a proposta para Negociando após o envio.
     * Default false quando omitido.
     */
    private Boolean allowNegotiation;
}
