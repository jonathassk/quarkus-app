package org.example.application.services.notification;

import org.example.application.dto.notification.MarkNotificationsReadRequest;
import org.example.application.dto.notification.NotificationDTO;
import org.example.application.services.chat.ChatBroadcastService;
import org.example.application.services.email.EmailPreferencesService;
import org.example.domain.entity.Notification;
import org.example.domain.entity.User;
import org.example.domain.entity.UserEmailPreferences;
import org.example.domain.enums.NotificationKind;
import org.example.domain.repository.NotificationRepository;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.email.EmailWorkerInvoker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.isA;

class NotificationServiceTest {

    private NotificationRepository notificationRepository;
    private UserRepository userRepository;
    private EmailPreferencesService emailPreferencesService;
    private EmailWorkerInvoker emailWorkerInvoker;
    private ChatBroadcastService chatBroadcastService;
    private NotificationService notificationService;

    private UUID userId;
    private User user;
    private UserEmailPreferences prefs;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        userRepository = mock(UserRepository.class);
        emailPreferencesService = mock(EmailPreferencesService.class);
        emailWorkerInvoker = mock(EmailWorkerInvoker.class);
        chatBroadcastService = mock(ChatBroadcastService.class);
        notificationService =
                new NotificationService(
                        notificationRepository,
                        userRepository,
                        emailPreferencesService,
                        emailWorkerInvoker,
                        chatBroadcastService);

        userId = UUID.randomUUID();
        user = User.builder().fullName("Ana").email("ana@example.com").build();
        user.id = userId;
        prefs =
                UserEmailPreferences.builder()
                        .user(user)
                        .emailUpdates(true)
                        .tripReminders(true)
                        .documentExpiryAlerts(true)
                        .inAppNotifications(true)
                        .activityEmails(true)
                        .build();

        when(userRepository.findById(userId)).thenReturn(user);
        when(emailPreferencesService.getOrCreate(userId)).thenReturn(prefs);
    }

    @Test
    void createPersistsBroadcastsAndOptionallyEmails() {
        NotificationDTO dto =
                notificationService.create(
                        userId,
                        NotificationKind.TRIP_COMMENT,
                        "Novo comentário",
                        "Olá",
                        "TRIP",
                        UUID.randomUUID(),
                        true);

        assertNotNull(dto);
        assertEquals(NotificationKind.TRIP_COMMENT, dto.getKind());
        assertTrue(dto.getDeepLink().startsWith("/plan/"));
        verify(notificationRepository).persist(isA(Notification.class));
        verify(chatBroadcastService).broadcastNotification(eq(userId), any(NotificationDTO.class));
        verify(emailWorkerInvoker)
                .enqueueDirectEmail(eq("ana@example.com"), eq("Novo comentário"), anyString(), anyString());
    }

    @Test
    void createSkipsInAppWhenPrefOff() {
        prefs.setInAppNotifications(false);
        NotificationDTO dto =
                notificationService.create(
                        userId,
                        NotificationKind.CHAT_MESSAGE,
                        "Msg",
                        "oi",
                        "CONVERSATION",
                        UUID.randomUUID(),
                        false);

        assertNull(dto);
        verify(notificationRepository, never()).persist(isA(Notification.class));
        verify(chatBroadcastService, never()).broadcastNotification(any(), any());
        verify(emailWorkerInvoker, never()).enqueueDirectEmail(any(), any(), any(), any());
    }

    @Test
    void proposalSentWithSendEmailFalseDoesNotEnqueue() {
        notificationService.create(
                userId,
                NotificationKind.PROPOSAL_SENT,
                "Enviada",
                "ok",
                "TRIP",
                UUID.randomUUID(),
                false);

        verify(emailWorkerInvoker, never()).enqueueDirectEmail(any(), any(), any(), any());
        verify(notificationRepository).persist(isA(Notification.class));
    }

    @Test
    void docExpiringEmailRespectsDocumentExpiryAlerts() {
        prefs.setDocumentExpiryAlerts(false);
        prefs.setActivityEmails(true);
        notificationService.notifyDocumentExpiring(
                userId, UUID.randomUUID(), "Passaporte vence", "corpo", true);

        verify(emailWorkerInvoker, never()).enqueueDirectEmail(any(), any(), any(), any());
        verify(notificationRepository).persist(isA(Notification.class));
    }

    @Test
    void markReadRequiresIdsOrAll() {
        MarkNotificationsReadRequest req = new MarkNotificationsReadRequest();
        assertThrows(
                jakarta.ws.rs.BadRequestException.class,
                () -> notificationService.markRead(userId, req));
    }

    @Test
    void createForUsersDeduplicatesRecipients() {
        notificationService.createForUsers(
                List.of(userId, userId),
                NotificationKind.EVENT_RSVP,
                "RSVP",
                "aceito",
                "EVENT",
                UUID.randomUUID(),
                false);

        verify(notificationRepository, times(1)).persist(isA(Notification.class));
    }
}
