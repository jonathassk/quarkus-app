package org.example.domain.enums;

/** Severidade de alerta de prazo / pendência. */
public enum OperationalAlertLevel {
    CRITICAL,
    WARNING,
    INFO;

    public static OperationalAlertLevel fromString(String value) {
        if (value == null || value.isBlank()) {
            return INFO;
        }
        for (OperationalAlertLevel l : values()) {
            if (l.name().equalsIgnoreCase(value.trim())) {
                return l;
            }
        }
        return INFO;
    }
}
