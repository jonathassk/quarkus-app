package org.example.application.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.example.domain.entity.User;
import org.example.domain.enums.UserType;
import org.example.domain.repository.UserRepository;

/**
 * Serviço de "claim" — transição de um usuário GUEST para FREE ou PREMIUM.
 *
 * <p><strong>Cenário:</strong> Um passageiro foi cadastrado por uma agência como
 * {@link UserType#GUEST} (sem credenciais B2C). Mais tarde, ele cria uma conta
 * individual no Baggagi usando o <em>mesmo e-mail</em>. O sistema deve:
 *
 * <ol>
 *   <li>Detectar que já existe um registro GUEST com aquele e-mail.</li>
 *   <li>Elevar o {@code userType} de {@code GUEST} para {@code FREE}.</li>
 *   <li>Vincular o {@code authUserId} Neon Auth ao registro existente.</li>
 *   <li>Preservar todos os vínculos com viagens de agência ({@code trip_users}).</li>
 * </ol>
 *
 * <p>A lógica é chamada pelo {@link org.example.application.services.impl.UserSyncService}
 * durante o fluxo JIT de primeiro login B2C.
 *
 * <p><strong>Segurança:</strong> A unificação só ocorre quando o e-mail do Neon Auth
 * coincide exatamente com o e-mail do GUEST — o próprio Neon Auth já validou
 * a posse do e-mail antes de emitir o JWT.
 */
@Slf4j
@ApplicationScoped
public class GuestClaimService {

    @Inject
    UserRepository userRepository;

    /**
     * Verifica se o usuário identificado pelo email é um GUEST e, se for,
     * realiza o claim: eleva para FREE e vincula o authUserId do Neon Auth.
     *
     * @param email      E-mail verificado pelo Neon Auth (já validado).
     * @param authUserId ID do Neon Auth ({@code sub} do JWT).
     * @param name       Nome retornado pelo Neon Auth, para atualizar o perfil.
     * @param pictureUrl URL da foto de perfil, se disponível.
     * @return O usuário atualizado se era GUEST (claim efetuado),
     *         ou {@code null} se não havia GUEST com esse e-mail.
     */
    @Transactional
    public User claimIfGuest(String email, String authUserId, String name, String pictureUrl) {
        if (email == null || email.isBlank()) {
            return null;
        }

        String normalizedEmail = email.trim().toLowerCase();

        return userRepository.findByEmail(normalizedEmail)
                .filter(u -> UserType.GUEST == u.getUserType())
                .map(guest -> performClaim(guest, authUserId, name, pictureUrl))
                .orElse(null);
    }

    // -------------------------------------------------------------------------

    private User performClaim(User guest, String authUserId, String name, String pictureUrl) {
        log.info(
                "Guest claim: upgrading userId={} email={} from GUEST to FREE (authUserId={})",
                guest.id, guest.getEmail(), authUserId);

        // 1. Eleva o tipo de usuário
        guest.setUserType(UserType.FREE);

        // 2. Vincula ao Neon Auth (sem sobrescrever se já estava preenchido de outra forma)
        if (guest.getAuthUserId() == null || guest.getAuthUserId().isBlank()) {
            guest.setAuthUserId(authUserId);
        }
        guest.setProviderId(authUserId);
        guest.setProvider("neon");

        // 3. Remove o marcador de Magic Link do passwordHash
        if ("*MAGIC_LINK*".equals(guest.getPasswordHash())) {
            guest.setPasswordHash("*EXTERNAL_AUTH*");
        }

        // 4. Atualiza perfil com dados frescos do Neon Auth
        guest.setEmailVerified(true);
        guest.setAccountStatus("active");

        if (name != null && !name.isBlank()) {
            guest.setFullName(name.trim());
        }
        if (shouldUpdateProfilePicture(guest.getProfilePictureUrl(), pictureUrl)) {
            guest.setProfilePictureUrl(pictureUrl);
        }

        // 5. role permanece USER — apenas o userType muda
        guest.setRole("USER");

        // Panache persiste automaticamente via dirty-checking; flush implícito no commit.
        log.info(
                "Guest claim complete: userId={} email={} — trip history preserved, userType=FREE",
                guest.id, guest.getEmail());

        return guest;
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
