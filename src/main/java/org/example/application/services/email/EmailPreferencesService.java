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
            prefs.setEmailUpdates(request.isEmailUpdates());
            prefs.setTripReminders(request.isTripReminders());
            prefs.setDocumentExpiryAlerts(request.isDocumentExpiryAlerts());
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
                .build();
    }
}
