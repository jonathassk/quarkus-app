package org.example.application.services.chat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.application.dto.chat.MessageDTO;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.LambdaException;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ChatBroadcastService {

    private final LambdaClient lambdaClient;

    @ConfigProperty(name = "chat.ws-broadcast-lambda-arn")
    Optional<String> broadcastLambdaArn;

    @ConfigProperty(name = "chat.enabled", defaultValue = "true")
    boolean chatEnabled;

    public void broadcastMessageNew(UUID conversationId, MessageDTO message) {
        if (!chatEnabled || message == null) {
            return;
        }
        String arn = broadcastLambdaArn.filter(s -> !s.isBlank()).orElse(null);
        if (arn == null) {
            log.debug("Chat broadcast skipped: CHAT_WS_BROADCAST_LAMBDA_ARN not configured");
            return;
        }

        JsonObjectBuilder payloadBuilder = Json.createObjectBuilder()
                .add("type", "message.new")
                .add("conversationId", conversationId.toString());

        JsonObjectBuilder messageBuilder = Json.createObjectBuilder()
                .add("id", message.getId())
                .add("conversationId", message.getConversationId())
                .add("senderId", message.getSenderId() != null ? message.getSenderId().toString() : null)
                .add("content", message.getContent())
                .add("contentType", message.getContentType().name())
                .add("createdAt", message.getCreatedAt());

        if (message.getSenderName() != null) {
            messageBuilder.add("senderName", message.getSenderName());
        }
        if (message.getSenderAvatarUrl() != null) {
            messageBuilder.add("senderAvatarUrl", message.getSenderAvatarUrl());
        }

        JsonObject event =
                payloadBuilder.add("payload", messageBuilder.build()).build();

        try {
            InvokeRequest request =
                    InvokeRequest.builder()
                            .functionName(arn)
                            .invocationType(InvocationType.EVENT)
                            .payload(SdkBytes.fromUtf8String(event.toString()))
                            .build();
            lambdaClient.invoke(request);
            log.debug(
                    "Chat broadcast invoked conversationId={} messageId={}",
                    conversationId,
                    message.getId());
        } catch (LambdaException e) {
            log.warn(
                    "Chat broadcast Lambda invoke failed conversationId={} messageId={}: {}",
                    conversationId,
                    message.getId(),
                    e.getMessage());
        }
    }

    public void broadcastInboxUpdated(UUID conversationId, UUID userId, int unreadCount) {
        if (!chatEnabled) {
            return;
        }
        String arn = broadcastLambdaArn.filter(s -> !s.isBlank()).orElse(null);
        if (arn == null) {
            return;
        }

        JsonObject event =
                Json.createObjectBuilder()
                        .add("type", "inbox.updated")
                        .add(
                                "payload",
                                Json.createObjectBuilder()
                                        .add("conversationId", conversationId.toString())
                                        .add("userId", userId.toString())
                                        .add("unreadCount", unreadCount)
                                        .build())
                        .build();

        try {
            lambdaClient.invoke(
                    InvokeRequest.builder()
                            .functionName(arn)
                            .invocationType(InvocationType.EVENT)
                            .payload(SdkBytes.fromUtf8String(event.toString()))
                            .build());
        } catch (LambdaException e) {
            log.warn(
                    "Chat inbox broadcast failed conversationId={} userId={}: {}",
                    conversationId,
                    userId,
                    e.getMessage());
        }
    }
}
