package org.example.application.exception.event;

import lombok.Getter;

@Getter
public class EventException extends RuntimeException {

    private final int status;
    private final String code;
    private final String eventId;

    public EventException(int status, String code, String message) {
        this(status, code, message, null);
    }

    public EventException(int status, String code, String message, String eventId) {
        super(message);
        this.status = status;
        this.code = code;
        this.eventId = eventId;
    }

    public static EventException notFound() {
        return new EventException(404, "NOT_FOUND", "Event not found");
    }

    public static EventException validation(String message) {
        return new EventException(400, "VALIDATION_ERROR", message);
    }

    public static EventException forbidden(String message) {
        return new EventException(403, "FORBIDDEN", message);
    }

    public static EventException alreadyExists(String eventId) {
        return new EventException(409, "EVENT_ALREADY_EXISTS", "Event already exists for this activity", eventId);
    }

    public static EventException rateLimited() {
        return new EventException(429, "RATE_LIMITED", "Too many requests. Try again later.");
    }

    public static EventException disabled() {
        return new EventException(503, "EVENTS_DISABLED", "Events feature is disabled");
    }
}
