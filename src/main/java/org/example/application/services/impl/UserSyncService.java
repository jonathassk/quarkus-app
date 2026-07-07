package org.example.application.services.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.example.application.services.GuestClaimService;
import org.example.domain.entity.User;
import org.example.domain.enums.UserType;
import org.example.domain.repository.UserRepository;

import java.util.Optional;

/**
 * Provisionamento JIT: vincula usuários do Neon Auth ({@code sub} UUID) ao registro em {@code users}.
 *
 * <p>Ordem de resolução:
 * <ol>
 *   <li>Busca por {@code auth_user_id} (ID do Neon Auth)</li>
 *   <li>Busca por {@code provider} + {@code provider_id}</li>
 *   <li>Busca por e-mail e vincula (mesma conta Google que existia antes da migração)</li>
 *   <li>Cria novo usuário</li>
 * </ol>
 */
@Slf4j
@ApplicationScoped
public class UserSyncService {

    private final UserRepository userRepository;

    @Inject
    GuestClaimService guestClaimService;

    @Inject
    public UserSyncService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public String resolveOrCreate(String authUserId, String email, String name) {
        return resolveOrCreateUser(authUserId, email, name, "neon", null).id.toString();
    }

    @Transactional
    public String resolveOrCreate(
            String authUserId, String email, String name, String provider, String pictureUrl) {
        return resolveOrCreateUser(authUserId, email, name, provider, pictureUrl).id.toString();
    }

    @Transactional
    public User resolveOrCreateUser(String authUserId, String email, String name) {
        return resolveOrCreateUser(authUserId, email, name, "neon", null);
    }

    @Transactional
    public User resolveOrCreateUser(
            String authUserId,
            String email,
            String name,
            String provider,
            String pictureUrl) {
        String resolvedProvider =
                provider != null && !provider.isBlank() ? provider.trim().toLowerCase() : "neon";

        Optional<User> byAuthId = userRepository.findByAuthUserId(authUserId);
        if (byAuthId.isPresent()) {
            User user = enrichExistingUser(
                    byAuthId.get(), authUserId, email, name, resolvedProvider, pictureUrl);
            log.debug("JIT: resolved user id={} by authUserId", user.id);
            return user;
        }

        Optional<User> byProvider =
                userRepository.findByProviderAndId(resolvedProvider, authUserId);
        if (byProvider.isPresent()) {
            User user =
                    enrichExistingUser(
                            byProvider.get(), authUserId, email, name, resolvedProvider, pictureUrl);
            log.debug("JIT: resolved user id={} by provider+providerId", user.id);
            return user;
        }

        String normalizedEmail =
                email != null && !email.isBlank() ? email.trim().toLowerCase() : null;
        if (normalizedEmail != null) {
            // Tenta o claim de Guest antes do enrich normal.
            // Se o usuário for GUEST com esse e-mail, ele é elevado para FREE
            // e seus vínculos com viagens de agência são preservados.
            User claimed = guestClaimService.claimIfGuest(
                    normalizedEmail, authUserId, name, pictureUrl);
            if (claimed != null) {
                log.info(
                        "JIT: guest claim completed for userId={} email={} — now FREE",
                        claimed.id, normalizedEmail);
                return claimed;
            }

            Optional<User> byEmail = userRepository.findByEmail(normalizedEmail);
            if (byEmail.isPresent()) {
                User user =
                        enrichExistingUser(
                                byEmail.get(), authUserId, email, name, resolvedProvider, pictureUrl);
                log.info(
                        "JIT: linked Neon/Google auth to existing user id={} (email match)",
                        user.id);
                return user;
            }
        }

        String base = deriveBase(email, authUserId);
        String username = uniqueUsername(base);
        String fullName = (name != null && !name.isBlank()) ? name : username;
        String userEmail =
                normalizedEmail != null ? normalizedEmail : authUserId + "@neon-auth.invalid";

        boolean oauthProvider = isOAuthProvider(resolvedProvider);

        User newUser =
                User.builder()
                        .authUserId(authUserId)
                        .email(userEmail)
                        .fullName(fullName)
                        .username(username)
                        .provider(resolvedProvider)
                        .providerId(authUserId)
                        .profilePictureUrl(pictureUrl)
                        .passwordHash("*EXTERNAL_AUTH*")
                        .emailVerified(normalizedEmail != null || oauthProvider)
                        .accountStatus("active")
                        .role("USER")
                        .userType(UserType.FREE)
                        .build();

        userRepository.CreateUser(newUser);
        log.info(
                "JIT: created user id={} authUserId={} provider={} email={}",
                newUser.id,
                authUserId,
                resolvedProvider,
                userEmail);
        return newUser;
    }

    /**
     * Vincula conta Neon/Google a registro existente (ex.: cadastro antigo só com e-mail/senha na tabela).
     */
    private User enrichExistingUser(
            User user,
            String authUserId,
            String email,
            String name,
            String provider,
            String pictureUrl) {
        if (user.getAuthUserId() == null || user.getAuthUserId().isBlank()) {
            user.setAuthUserId(authUserId);
        }
        if (provider != null && !provider.isBlank()) {
            user.setProvider(provider);
        }
        user.setProviderId(authUserId);

        String normalizedEmail =
                email != null && !email.isBlank() ? email.trim().toLowerCase() : null;
        if (normalizedEmail != null) {
            user.setEmail(normalizedEmail);
            user.setEmailVerified(true);
        } else if (isOAuthProvider(provider)) {
            user.setEmailVerified(true);
        }

        if (name != null && !name.isBlank()) {
            user.setFullName(name.trim());
        }
        if (shouldUpdateProfilePicture(user.getProfilePictureUrl(), pictureUrl)) {
            user.setProfilePictureUrl(pictureUrl);
        }

        if (isOAuthProvider(provider)) {
            user.setPasswordHash("*EXTERNAL_AUTH*");
        }

        touchLastLogin(user);
        return user;
    }

    private static boolean isOAuthProvider(String provider) {
        if (provider == null) {
            return false;
        }
        return switch (provider.toLowerCase()) {
            case "google", "facebook", "github", "apple", "microsoft" -> true;
            default -> false;
        };
    }

    private void touchLastLogin(User user) {
        user.setLastLoginAt(java.time.Instant.now());
    }

    /**
     * Placeholder quando só há e-mail/nome (fluxos legados); session-sync com JWT preenche {@code auth_user_id}.
     */
    @Transactional
    public User resolveOrCreateGoogleByEmail(String email, String name) {
        String normalized = email.trim().toLowerCase();
        return userRepository
                .findByEmail(normalized)
                .map(
                        existing ->
                                enrichExistingUser(
                                        existing, existing.getAuthUserId(), normalized, name, "google", null))
                .orElseGet(
                        () -> {
                            String base = deriveBase(normalized, normalized);
                            String username = uniqueUsername(base);
                            String fullName =
                                    (name != null && !name.isBlank()) ? name.trim() : username;
                            User created =
                                    User.builder()
                                            .email(normalized)
                                            .fullName(fullName)
                                            .username(username)
                                            .provider("google")
                                            .providerId(normalized)
                                            .passwordHash("*EXTERNAL_AUTH*")
                                            .emailVerified(true)
                                            .accountStatus("active")
                                            .role("USER")
                                            .build();
                            userRepository.CreateUser(created);
                            log.info("JIT: created Google placeholder user id={}", created.id);
                            return created;
                        });
    }

    private String deriveBase(String email, String sub) {
        if (email != null && email.contains("@")) {
            String base =
                    email.split("@")[0].replaceAll("[^a-zA-Z0-9._-]", "_").toLowerCase();
            if (base.length() >= 3) {
                return base;
            }
        }
        return "user_" + sub.substring(0, Math.min(8, sub.length()));
    }

    private String uniqueUsername(String base) {
        if (userRepository.findByUsername(base).isEmpty()) {
            return base;
        }
        for (int i = 2; i <= 999; i++) {
            String candidate = base + i;
            if (userRepository.findByUsername(candidate).isEmpty()) {
                return candidate;
            }
        }
        return base + "_" + System.currentTimeMillis();
    }

    private boolean shouldUpdateProfilePicture(String currentPic, String newPic) {
        if (newPic == null || newPic.isBlank()) {
            return false;
        }
        if (currentPic == null || currentPic.isBlank()) {
            return true;
        }
        // Se a foto atual do usuário contém "avatars/" (foto enviada pelo próprio usuário no Baggagi),
        // não devemos sobrescrever com a foto do Google/redes sociais.
        if (currentPic.contains("avatars/")) {
            return false;
        }
        return true;
    }
}
