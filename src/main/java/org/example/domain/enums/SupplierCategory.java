package org.example.domain.enums;

/** Categoria de fornecedor. */
public enum SupplierCategory {
    AIRLINE,
    HOTEL,
    TRANSFER,
    ACTIVITY,
    INSURANCE,
    CAR_RENTAL,
    DMC,
    OTHER;

    public static SupplierCategory fromString(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        for (SupplierCategory c : values()) {
            if (c.name().equalsIgnoreCase(value.trim())) {
                return c;
            }
        }
        return OTHER;
    }

    public static SupplierCategory fromServiceType(OperationalServiceType type) {
        if (type == null) {
            return OTHER;
        }
        return switch (type) {
            case FLIGHT -> AIRLINE;
            case HOTEL -> HOTEL;
            case TRANSFER -> TRANSFER;
            case ACTIVITY -> ACTIVITY;
            case INSURANCE -> INSURANCE;
            case CAR_RENTAL -> CAR_RENTAL;
            case OTHER -> OTHER;
        };
    }
}
