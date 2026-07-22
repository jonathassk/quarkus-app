package org.example.application.services.chat;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.application.dto.chat.ChatEligibilityDTO;
import org.example.application.dto.chat.ConversationInboxItemDTO;
import org.example.application.dto.chat.UserPrivacySettingsDTO;
import org.example.application.exception.chat.ChatException;
import org.example.domain.entity.User;
import org.example.domain.entity.chat.Conversation;
import org.example.domain.entity.chat.ConversationParticipant;
import org.example.domain.entity.chat.DirectConversationPair;
import org.example.domain.entity.chat.UserPrivacySettings;
import org.example.domain.enums.ConversationStatus;
import org.example.domain.enums.ConversationType;
import org.example.domain.repository.UserRepository;
import org.example.domain.repository.chat.ConversationParticipantRepository;
import org.example.domain.repository.chat.ConversationRepository;
import org.example.domain.repository.chat.DirectConversationPairRepository;
import org.example.domain.repository.chat.UserFollowRepository;
import org.example.domain.repository.chat.UserPrivacySettingsRepository;

@ApplicationScoped
@RequiredArgsConstructor
public class PrivacyService {

    private final UserPrivacySettingsRepository privacyRepository;
    private final UserRepository userRepository;

    @Transactional
    public UserPrivacySettingsDTO getPrivacy(UUID userId) {
        UserPrivacySettings settings = getOrCreate(userId);
        return toDto(settings);
    }

    @Transactional
    public UserPrivacySettingsDTO updatePrivacy(UUID userId, UserPrivacySettingsDTO request) {
        UserPrivacySettings settings = getOrCreate(userId);
        if (request != null) {
            settings.setPublicProfile(request.isPublicProfile());
            if (request.getAllowDmPublic() != null) {
                settings.setAllowDmPublic(request.getAllowDmPublic());
            }
            if (request.getAllowDmFollowersOnly() != null) {
                settings.setAllowDmFollowersOnly(request.getAllowDmFollowersOnly());
            }
        }
        privacyRepository.persist(settings);
        return toDto(settings);
    }

    @Transactional
    public UserPrivacySettings getOrCreate(UUID userId) {
        User user = userRepository.findById(userId);
        if (user == null) {
            throw ChatException.notFound("User not found");
        }
        UserPrivacySettings settings = privacyRepository.findById(userId);
        if (settings == null) {
            settings =
                    UserPrivacySettings.builder()
                            .user(user)
                            .publicProfile(false)
                            .allowDmPublic(true)
                            .allowDmFollowersOnly(true)
                            .build();
            privacyRepository.persist(settings);
        }
        return settings;
    }

    private UserPrivacySettingsDTO toDto(UserPrivacySettings settings) {
        return UserPrivacySettingsDTO.builder()
                .publicProfile(settings.isPublicProfile())
                .allowDmPublic(settings.isAllowDmPublic())
                .allowDmFollowersOnly(settings.isAllowDmFollowersOnly())
                .build();
    }
}
