package org.example.application.services.email;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.application.dto.email.UserEmailPreferencesDTO;
import org.example.domain.entity.User;
import org.example.domain.entity.UserEmailPreferences;
import org.example.domain.repository.UserEmailPreferencesRepository;
import org.example.domain.repository.UserRepository;

import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class EmailPreferencesService {

    private final UserEmailPreferencesRepository preferencesRepository;
    private final UserRepository userRepository;

    @Transactional
    public UserEmailPreferencesDTO getPreferences(UUID userId) {
        return toDto(getOrCreate(userId));
    }

    @Transactional
    public UserEmailPreferencesDTO updatePreferences(UUID userId, UserEmailPreferencesDTO request) {
        UserEmailPreferences prefs = getOrCreate(userId);
        if (request != null) {
            // Só atualiza campos presentes (Boolean nullable) — evita zerar omitidos no PATCH.
            if (request.getEmailUpdates() != null) {
                prefs.setEmailUpdates(request.getEmailUpdates());
            }
            if (request.getTripReminders() != null) {
                prefs.setTripReminders(request.getTripReminders());
            }
            if (request.getDocumentExpiryAlerts() != null) {
                prefs.setDocumentExpiryAlerts(request.getDocumentExpiryAlerts());
            }
            if (request.getInAppNotifications() != null) {
                prefs.setInAppNotifications(request.getInAppNotifications());
            }
            if (request.getActivityEmails() != null) {
                prefs.setActivityEmails(request.getActivityEmails());
            }
        }
        preferencesRepository.persist(prefs);
        return toDto(prefs);
    }

    @Transactional
    public UserEmailPreferences getOrCreate(UUID userId) {
        User user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        UserEmailPreferences prefs = preferencesRepository.findById(userId);
        if (prefs == null) {
            prefs = UserEmailPreferences.builder()
                    .user(user)
                    .emailUpdates(true)
                    .tripReminders(true)
                    .documentExpiryAlerts(true)
                    .inAppNotifications(true)
                    .activityEmails(true)
                    .build();
            preferencesRepository.persist(prefs);
        }
        return prefs;
    }

    private UserEmailPreferencesDTO toDto(UserEmailPreferences prefs) {
        return UserEmailPreferencesDTO.builder()
                .emailUpdates(prefs.isEmailUpdates())
                .tripReminders(prefs.isTripReminders())
                .documentExpiryAlerts(prefs.isDocumentExpiryAlerts())
                .inAppNotifications(prefs.isInAppNotifications())
                .activityEmails(prefs.isActivityEmails())
                .build();
    }
}
