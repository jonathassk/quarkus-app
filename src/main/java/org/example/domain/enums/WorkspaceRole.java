package org.example.domain.enums;

import lombok.Getter;

@Getter
public enum WorkspaceRole {
    OWNER("owner", 2),
    AGENT("agent", 1),
    VIEWER("viewer", 0);

    private final String description;
    private final int hierarchyLevel;

    WorkspaceRole(String description, int hierarchyLevel) {
        this.description = description;
        this.hierarchyLevel = hierarchyLevel;
    }

    public static WorkspaceRole fromString(String value) {
        for (WorkspaceRole role : values()) {
            if (role.name().equalsIgnoreCase(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Nível de permissão inválido para workspace: " + value);
    }
}
