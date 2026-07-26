package org.example.domain.enums;

/**
 * Benefício liberado em uma viagem específica por um pagamento avulso (UNITARIO).
 *
 * <p>Limites de plano continuam no nível de workspace/usuário; estes kinds valem
 * apenas para a viagem paga.
 */
public enum TripUnlockKind {
    /** Export do roteiro em PDF. */
    EXPORT_PDF,
    /** Gerações de roteiro por IA na viagem. */
    AI_GENERATIONS
}
