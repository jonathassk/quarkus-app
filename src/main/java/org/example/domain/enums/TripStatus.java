package org.example.domain.enums;

import java.time.LocalDate;

/**
 * Lifecycle of a trip relative to calendar dates.
 * <ul>
 *   <li>{@link #PLANNING} — before {@code startDate}, or missing dates</li>
 *   <li>{@link #ONGOING} — {@code startDate} ≤ today ≤ {@code endDate}</li>
 *   <li>{@link #COMPLETED} — after {@code endDate}</li>
 * </ul>
 */
public enum TripStatus {
    PLANNING,
    ONGOING,
    COMPLETED;

    public static TripStatus fromDates(LocalDate startDate, LocalDate endDate, LocalDate today) {
        if (startDate == null || endDate == null) {
            return PLANNING;
        }
        if (today.isBefore(startDate)) {
            return PLANNING;
        }
        if (today.isAfter(endDate)) {
            return COMPLETED;
        }
        return ONGOING;
    }
}
