package org.example.domain.enums;

/** Motivos tipados de solicitação de alteração pelo cliente. */
public enum ChangeRequestType {
    CHANGE_HOTEL,
    CHANGE_FLIGHT,
    CHANGE_DATES,
    REMOVE_SERVICE,
    ADD_SERVICE,
    REVIEW_PRICE,
    OTHER;

    public static ChangeRequestType fromString(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        for (ChangeRequestType t : values()) {
            if (t.name().equalsIgnoreCase(value.trim())) {
                return t;
            }
        }
        return OTHER;
    }
}
