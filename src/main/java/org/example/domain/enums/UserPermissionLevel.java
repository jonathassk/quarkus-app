package org.example.domain.enums;

import lombok.Getter;

@Getter
public enum UserPermissionLevel {
    OWNER("owner", 2),
    ADMIN("admin", 1),
    VIEWER("viewer", 0);

    private final String description;
    private final int hierarchyLevel;

    UserPermissionLevel(String description, int hierarchyLevel) {
        this.description = description;
        this.hierarchyLevel = hierarchyLevel;
    }

    public boolean canEdit() {
        return this.hierarchyLevel >= ADMIN.hierarchyLevel;
    }

    public boolean canManageUsers() {
        return this.hierarchyLevel >= OWNER.hierarchyLevel;
    }

    public boolean canDelete() {
            return this == OWNER;
    }

    public static UserPermissionLevel fromString(String value) {
        for (UserPermissionLevel level : values()) {
            if (level.name().equalsIgnoreCase(value)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Nível de permissão inválido: " + value);
    }
}
