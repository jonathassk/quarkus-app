package org.example.domain.enums;

import lombok.Getter;

/**
 * Papel de um membro dentro de uma agência de viagens.
 *
 * <ul>
 *   <li>{@link #AGENCY_OWNER}      – dono da agência; acesso total a todas as viagens da agência.</li>
 *   <li>{@link #AGENCY_CONSULTANT} – consultor/funcionário; acesso às viagens que criou ou nas
 *                                    quais foi adicionado como membro.</li>
 * </ul>
 */
@Getter
public enum AgencyRole {
    AGENCY_OWNER("Agency Owner", 2),
    AGENCY_CONSULTANT("Agency Consultant", 1);

    private final String displayName;
    private final int hierarchyLevel;

    AgencyRole(String displayName, int hierarchyLevel) {
        this.displayName = displayName;
        this.hierarchyLevel = hierarchyLevel;
    }

    /** {@code true} se este papel tem acesso a todas as viagens da agência. */
    public boolean canViewAllAgencyTrips() {
        return this == AGENCY_OWNER;
    }

    /** {@code true} se este papel pode convidar novos membros para a agência. */
    public boolean canManageAgencyMembers() {
        return this == AGENCY_OWNER;
    }

    public static AgencyRole fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Agency role não pode ser nulo ou vazio");
        }
        for (AgencyRole role : values()) {
            if (role.name().equalsIgnoreCase(value.trim())) {
                return role;
            }
        }
        throw new IllegalArgumentException("Agency role inválido: " + value);
    }
}
