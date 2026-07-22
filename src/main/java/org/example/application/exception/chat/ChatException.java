package org.example.application.exception.chat;

import lombok.Getter;

@Getter
public class ChatException extends RuntimeException {

    private final int status;
    private final String code;

    public ChatException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ChatException notFound(String message) {
        return new ChatException(404, "CONVERSATION_NOT_FOUND", message);
    }

    public static ChatException notParticipant() {
        return new ChatException(403, "CHAT_NOT_PARTICIPANT", "You are not a participant of this conversation");
    }

    public static ChatException archived() {
        return new ChatException(403, "CHAT_ARCHIVED", "This conversation is archived");
    }

    public static ChatException followersOnly() {
        return new ChatException(403, "CHAT_FOLLOWERS_ONLY", "Direct messages require following this user");
    }

    public static ChatException validation(String message) {
        return new ChatException(400, "VALIDATION_ERROR", message);
    }

    public static ChatException alreadyExists(String message) {
        return new ChatException(409, "CONVERSATION_ALREADY_EXISTS", message);
    }

    public static ChatException forbidden(String message) {
        return new ChatException(403, "CHAT_FORBIDDEN", message);
    }
}
