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
}
