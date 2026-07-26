package org.example.application.services.email;

import org.example.application.dto.email.UserEmailPreferencesDTO;
import org.example.domain.entity.User;
import org.example.domain.entity.UserEmailPreferences;
import org.example.domain.repository.UserEmailPreferencesRepository;
import org.example.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailPreferencesServiceTest {

    private UserEmailPreferencesRepository preferencesRepository;
    private UserRepository userRepository;
    private EmailPreferencesService service;
    private UUID userId;
    private UserEmailPreferences prefs;

    @BeforeEach
    void setUp() {
        preferencesRepository = mock(UserEmailPreferencesRepository.class);
        userRepository = mock(UserRepository.class);
        service = new EmailPreferencesService(preferencesRepository, userRepository);

        userId = UUID.randomUUID();
        User user = User.builder().fullName("Bob").email("bob@example.com").build();
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
        when(preferencesRepository.findById(userId)).thenReturn(prefs);
    }

    @Test
    void partialPatchDoesNotZeroOmittedFields() {
        UserEmailPreferencesDTO request =
                UserEmailPreferencesDTO.builder().activityEmails(false).build();

        UserEmailPreferencesDTO updated = service.updatePreferences(userId, request);

        assertFalse(updated.getActivityEmails());
        assertTrue(updated.getInAppNotifications());
        assertTrue(updated.getEmailUpdates());
        assertTrue(updated.getTripReminders());
        assertTrue(updated.getDocumentExpiryAlerts());
    }

    @Test
    void getReturnsChannelDefaults() {
        UserEmailPreferencesDTO dto = service.getPreferences(userId);
        assertTrue(dto.getInAppNotifications());
        assertTrue(dto.getActivityEmails());
    }
}
