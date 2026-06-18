package org.example.application.services;

import io.smallrye.jwt.auth.principal.JWTParser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.example.domain.entity.Trip;
import org.example.domain.entity.User;
import org.example.domain.enums.UserType;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;
import org.example.utils.AuthTokenException;

import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.util.KeyUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Serviço de Magic Link JWT para autenticação de usuários GUEST.
 *
 * <p>Fluxo:
 * <ol>
 *   <li>Agente da agência cria viagem e vincula e-mail do cliente (Guest).</li>
 *   <li>Cliente solicita acesso → {@link #generateMagicLinkToken} emite JWT de curta duração.</li>
 *   <li>E-mail enviado com link para {@code /magic?token=<jwt>}.</li>
 *   <li>Frontend chama {@link #verifyMagicLinkToken} → retorna access token de sessão.</li>
 * </ol>
 *
 * <p><strong>Segurança:</strong>
 * <ul>
 *   <li>O Magic Link JWT expira em 15 minutos.</li>
 *   <li>A claim {@code type=magic_link} impede reuso de tokens de sessão normais.</li>
 *   <li>O e-mail deve estar vinculado à viagem (via trip_users ou users.email).</li>
 * </ul>
 */
@Slf4j
@ApplicationScoped
public class MagicLinkService {

    private static final Duration MAGIC_LINK_TTL = Duration.ofMinutes(15);
    private static final Duration SESSION_TTL = Duration.ofDays(7);
    private static final String MAGIC_LINK_TYPE = "magic_link";

    @Inject
    TripRepository tripRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    TokenService tokenService;

    @Inject
    JWTParser jwtParser;

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @ConfigProperty(name = "mp.jwt.sign.key.location", defaultValue = "privateKey.pem")
    String privateKeyLocation;

    /**
     * Gera um JWT de Magic Link para um e-mail vinculado a uma viagem.
     *
     * @param email  E-mail do usuário GUEST cadastrado na viagem.
     * @param tripId ID da viagem à qual o guest tem acesso.
     * @return JWT assinado, válido por 15 minutos.
     * @throws AuthTokenException se o e-mail não estiver vinculado à viagem.
     */
    public String generateMagicLinkToken(String email, Long tripId)
            throws GeneralSecurityException, IOException {

        String normalizedEmail = normalize(email);
        validateGuestEmailLinkedToTrip(normalizedEmail, tripId);

        PrivateKey privateKey = KeyUtils.readPrivateKey(privateKeyLocation);

        String token = Jwt.issuer(issuer)
                .upn(normalizedEmail)
                .subject(normalizedEmail)
                .claim("tripId", tripId)
                .claim("type", MAGIC_LINK_TYPE)
                .claim("userType", UserType.GUEST.name())
                .groups(Set.of("GUEST"))
                .expiresAt(Instant.now().plus(MAGIC_LINK_TTL))
                .sign(privateKey);

        log.info("Magic link token generated for email={} tripId={}", normalizedEmail, tripId);
        return token;
    }

    /**
     * Verifica um Magic Link JWT e retorna um access token de sessão.
     *
     * <p>Cria o usuário GUEST na base caso não exista (idempotente).
     *
     * @param magicToken JWT emitido por {@link #generateMagicLinkToken}.
     * @return Access token de sessão (JWT com userId, válido por 7 dias).
     * @throws AuthTokenException em caso de token inválido, expirado ou de tipo incorreto.
     */
    @Transactional
    public MagicLinkVerifyResult verifyMagicLinkToken(String magicToken)
            throws GeneralSecurityException, IOException {

        ParsedMagicLink parsed = parseMagicLinkJwt(magicToken);

        User guest = resolveOrCreateGuest(parsed.email(), parsed.tripId());

        // Emite access token de sessão normal reutilizando o TokenService existente
        String sessionToken = tokenService.generateToken(guest, null);

        log.info("Magic link verified: guestUserId={} email={} tripId={}",
                guest.id, parsed.email(), parsed.tripId());

        return new MagicLinkVerifyResult(sessionToken, guest.id, parsed.tripId());
    }

    // -------------------------------------------------------------------------
    // Tipos de retorno
    // -------------------------------------------------------------------------

    public record MagicLinkVerifyResult(String accessToken, Long userId, Long tripId) {}

    // -------------------------------------------------------------------------
    // Helpers privados
    // -------------------------------------------------------------------------

    private void validateGuestEmailLinkedToTrip(String email, Long tripId) {
        Trip trip = tripRepository.findById(tripId);
        if (trip == null) {
            throw new AuthTokenException("TRIP_NOT_FOUND", "Viagem não encontrada: " + tripId);
        }

        // Verifica se o e-mail está vinculado à viagem via trip_users
        boolean linked = trip.getUsers() != null && trip.getUsers().stream()
                .anyMatch(tu -> tu.getUser() != null
                        && email.equalsIgnoreCase(tu.getUser().getEmail()));

        // Ou se existe um user com esse e-mail que seja criador da viagem
        if (!linked && trip.getCreatedBy() != null) {
            linked = email.equalsIgnoreCase(trip.getCreatedBy().getEmail());
        }

        if (!linked) {
            log.warn("Magic link request rejected: email={} not linked to tripId={}", email, tripId);
            throw new AuthTokenException(
                    "EMAIL_NOT_LINKED",
                    "Este e-mail não está vinculado à viagem informada.");
        }
    }

    private ParsedMagicLink parseMagicLinkJwt(String rawToken) {
        try {
            JsonWebToken jwt = jwtParser.parse(rawToken.trim());

            // Valida que é um Magic Link, não um token de sessão
            Object type = jwt.getClaim("type");
            if (!MAGIC_LINK_TYPE.equals(String.valueOf(type))) {
                throw new AuthTokenException("INVALID_TOKEN_TYPE",
                        "Token inválido para este endpoint");
            }

            String email = jwt.getSubject();
            Object tripIdClaim = jwt.getClaim("tripId");
            if (email == null || tripIdClaim == null) {
                throw new AuthTokenException("INVALID_TOKEN", "Token malformado");
            }

            Long tripId = ((Number) tripIdClaim).longValue();
            return new ParsedMagicLink(normalize(email), tripId);

        } catch (AuthTokenException e) {
            throw e;
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("expired")) {
                throw new AuthTokenException("TOKEN_EXPIRED",
                        "O link expirou. Solicite um novo.", e);
            }
            throw new AuthTokenException("INVALID_TOKEN", "Token inválido ou malformado", e);
        }
    }

    private record ParsedMagicLink(String email, Long tripId) {}

    @Transactional
    private User resolveOrCreateGuest(String email, Long tripId) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    String base = email.split("@")[0]
                            .replaceAll("[^a-zA-Z0-9._-]", "_").toLowerCase();
                    String username = ensureUniqueUsername(base);

                    User guest = User.builder()
                            .email(email)
                            .fullName(username)
                            .username(username)
                            .userType(UserType.GUEST)
                            .role("GUEST")
                            .passwordHash("*MAGIC_LINK*")
                            .emailVerified(true)
                            .accountStatus("active")
                            .provider("magic_link")
                            .providerId(email)
                            .build();

                    userRepository.CreateUser(guest);
                    log.info("GUEST user created via magic link: id={} email={} tripId={}",
                            guest.id, email, tripId);
                    return guest;
                });
    }

    private String ensureUniqueUsername(String base) {
        if (userRepository.findByUsername(base).isEmpty()) return base;
        for (int i = 2; i <= 999; i++) {
            String candidate = base + i;
            if (userRepository.findByUsername(candidate).isEmpty()) return candidate;
        }
        return base + "_" + System.currentTimeMillis();
    }

    private static String normalize(String email) {
        if (email == null || email.isBlank()) {
            throw new AuthTokenException("INVALID_EMAIL", "E-mail é obrigatório");
        }
        return email.trim().toLowerCase();
    }
}
