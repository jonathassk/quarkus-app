package org.example.domain.enums;

/**
 * Escopo da listagem do pipeline: kanban ativo ou histórico/arquivo.
 */
public enum PipelineScope {
    ACTIVE,
    ARCHIVE,
    ALL;

    public static PipelineScope fromString(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        for (PipelineScope s : values()) {
            if (s.name().equalsIgnoreCase(value.trim())) {
                return s;
            }
        }
        throw new IllegalArgumentException("pipeline scope inválido: " + value);
    }
}
