package org.example.application.services.notification;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.notification.MarkNotificationsReadRequest;
import org.example.application.dto.notification.MarkNotificationsReadResponse;
import org.example.application.dto.notification.NotificationDTO;
import org.example.application.dto.notification.NotificationsPageDTO;
import org.example.application.services.chat.ChatBroadcastService;
import org.example.application.services.email.EmailPreferencesService;
import org.example.domain.entity.Notification;
import org.example.domain.entity.User;
import org.example.domain.entity.UserEmailPreferences;
import org.example.domain.enums.NotificationKind;
import org.example.domain.repository.NotificationRepository;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.email.EmailWorkerInvoker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailPreferencesService emailPreferencesService;
    private final EmailWorkerInvoker emailWorkerInvoker;
    private final ChatBroadcastService chatBroadcastService;

    @Transactional
    public NotificationsPageDTO list(UUID userId, int page, int size, boolean unreadOnly) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        List<Notification> rows =
                notificationRepository.findPage(userId, unreadOnly, safePage, safeSize);
        long total = notificationRepository.countByUser(userId, unreadOnly);
        long unread = notificationRepository.countUnread(userId);
        boolean hasMore = (long) (safePage + 1) * safeSize < total;
        return NotificationsPageDTO.builder()
                .items(rows.stream().map(this::toDto).collect(Collectors.toList()))
                .total(total)
                .page(safePage)
                .size(safeSize)
                .hasMore(hasMore)
                .unreadCount(unread)
                .build();
    }

    @Transactional
    public MarkNotificationsReadResponse markRead(UUID userId, MarkNotificationsReadRequest request) {
        if (request == null) {
            throw new BadRequestException("request body is required");
        }
        int marked;
        if (request.isAll()) {
            marked = notificationRepository.markAllRead(userId);
        } else if (request.getIds() != null && !request.getIds().isEmpty()) {
            Instant now = Instant.now();
            List<Notification> owned =
                    notificationRepository.findOwnedByIds(userId, request.getIds());
            marked = 0;
            for (Notification n : owned) {
                if (n.getReadAt() == null) {
                    n.setReadAt(now);
                    marked++;
                }
            }
        } else {
            throw new BadRequestException("Provide ids or set all=true");
        }
        return MarkNotificationsReadResponse.builder()
                .marked(marked)
                .unreadCount(notificationRepository.countUnread(userId))
                .build();
    }

    /**
     * Cria notificação in-app (se {@code in_app_notifications}) e opcionalmente e-mail.
     *
     * @param sendEmail quando false, nunca enfileira e-mail (útil quando o fluxo já notifica
     *     por outro canal, ex. proposal_sent white-label ou document_expiry_reminders).
     */
    @Transactional
    public NotificationDTO create(
            UUID userId,
            NotificationKind kind,
            String title,
            String body,
            String entityType,
            UUID entityId,
            boolean sendEmail) {
        if (userId == null || kind == null) {
            return null;
        }
        User user = userRepository.findById(userId);
        if (user == null) {
            log.debug("Skip notification: user {} not found", userId);
            return null;
        }

        UserEmailPreferences prefs = emailPreferencesService.getOrCreate(userId);
        NotificationDTO dto = null;

        if (prefs.isInAppNotifications()) {
            Notification notification =
                    Notification.builder()
                            .user(user)
                            .kind(kind)
                            .title(truncate(title, 255))
                            .body(body)
                            .entityType(entityType)
                            .entityId(entityId)
                            .build();
            notificationRepository.persist(notification);
            dto = toDto(notification);
            try {
                chatBroadcastService.broadcastNotification(userId, dto);
            } catch (Exception e) {
                log.warn("Realtime notification broadcast failed userId={}: {}", userId, e.getMessage());
            }
        } else {
            log.debug("Skip in-app notification userId={} kind={} (pref off)", userId, kind);
        }

        if (sendEmail) {
            maybeEnqueueEmail(user, prefs, kind, title, body);
        }

        return dto;
    }

    /** Atalho: in-app + e-mail de atividade conforme preferências. */
    @Transactional
    public NotificationDTO create(
            UUID userId,
            NotificationKind kind,
            String title,
            String body,
            String entityType,
            UUID entityId) {
        return create(userId, kind, title, body, entityType, entityId, true);
    }

    @Transactional
    public List<NotificationDTO> createForUsers(
            Collection<UUID> userIds,
            NotificationKind kind,
            String title,
            String body,
            String entityType,
            UUID entityId,
            boolean sendEmail) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<UUID> unique = new LinkedHashSet<>(userIds);
        List<NotificationDTO> created = new ArrayList<>();
        for (UUID uid : unique) {
            NotificationDTO dto = create(uid, kind, title, body, entityType, entityId, sendEmail);
            if (dto != null) {
                created.add(dto);
            }
        }
        return created;
    }

    @Transactional
    public List<NotificationDTO> createForUsers(
            Collection<UUID> userIds,
            NotificationKind kind,
            String title,
            String body,
            String entityType,
            UUID entityId) {
        return createForUsers(userIds, kind, title, body, entityType, entityId, true);
    }

    /**
     * DOC_EXPIRING: in-app respeita {@code in_app_notifications}; e-mail (se {@code sendEmail})
     * respeita {@code document_expiry_alerts}. Preferir {@code sendEmail=false} quando o
     * email-worker já dispara {@code document_expiry_reminders}.
     */
    @Transactional
    public NotificationDTO notifyDocumentExpiring(
            UUID userId, UUID documentId, String title, String body, boolean sendEmail) {
        return create(
                userId,
                NotificationKind.DOC_EXPIRING,
                title,
                body,
                "DOCUMENT",
                documentId,
                sendEmail);
    }

    private void maybeEnqueueEmail(
            User user, UserEmailPreferences prefs, NotificationKind kind, String title, String body) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }
        boolean allowed =
                kind == NotificationKind.DOC_EXPIRING
                        ? prefs.isDocumentExpiryAlerts()
                        : prefs.isActivityEmails();
        if (!allowed) {
            log.debug("Skip activity email userId={} kind={} (pref off)", user.id, kind);
            return;
        }
        String subject = title != null ? title : "Nova notificação no Baggagi";
        String text = body != null ? body : subject;
        String html =
                "<p>"
                        + escapeHtml(text).replace("\n", "<br/>")
                        + "</p>";
        emailWorkerInvoker.enqueueDirectEmail(user.getEmail(), subject, text, html);
    }

    NotificationDTO toDto(Notification n) {
        return NotificationDTO.builder()
                .id(n.id)
                .kind(n.getKind())
                .title(n.getTitle())
                .body(n.getBody())
                .entityType(n.getEntityType())
                .entityId(n.getEntityId())
                .deepLink(NotificationDeepLinks.resolve(n.getKind(), n.getEntityType(), n.getEntityId()))
                .readAt(n.getReadAt())
                .createdAt(n.getCreatedAt())
                .build();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String escapeHtml(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
