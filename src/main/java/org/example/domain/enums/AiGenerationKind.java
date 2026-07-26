package org.example.domain.enums;

/**
 * Tipo de consumo de IA registrado em {@code ai_generations}.
 */
public enum AiGenerationKind {
    /** Geração completa de roteiro. */
    PLAN,
    /** Refino de um dia/segmento (épico 6). */
    REFINE;

    public static AiGenerationKind fromString(String value) {
        if (value == null || value.isBlank()) {
            return PLAN;
        }
        for (AiGenerationKind kind : values()) {
            if (kind.name().equalsIgnoreCase(value.trim())) {
                return kind;
            }
        }
        throw new IllegalArgumentException("ai generation kind inválido: " + value);
    }
}
