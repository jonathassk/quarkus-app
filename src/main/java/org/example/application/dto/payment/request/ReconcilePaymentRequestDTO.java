package org.example.application.dto.payment.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reaplica o plano a partir de uma Checkout Session ou Subscription Stripe já paga.
 * Usado quando o webhook falhou ou o plano ficou inconsistente (ex.: B2C após compra B2B).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconcilePaymentRequestDTO {
    /** ID da Checkout Session ({@code cs_...}), tipicamente o {@code session_id} da success URL. */
    private String sessionId;
    /** ID da Subscription Stripe ({@code sub_...}), alternativa quando não há session. */
    private String subscriptionId;
}
