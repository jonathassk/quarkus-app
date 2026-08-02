package org.example.application.services.trip;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.application.dto.trip.request.CreateTripInviteRequest;
import org.example.application.dto.trip.response.TripInviteDTO;
import org.example.application.services.TripCollaborationService;
import org.example.application.services.chat.TripChatService;
import org.example.application.services.notification.NotificationService;
import org.example.domain.entity.Trip;
import org.example.domain.entity.TripInvite;
import org.example.domain.entity.TripUser;
import org.example.domain.entity.User;
import org.example.domain.enums.NotificationKind;
import org.example.domain.enums.TripInviteStatus;
import org.example.domain.enums.UserPermissionLevel;
import org.example.domain.repository.TripInviteRepository;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.email.EmailWorkerInvoker;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class TripInviteService {

    private static final Duration DEFAULT_TTL = Duration.ofDays(7);
    private static final char[] TOKEN_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final TripRepository tripRepository;
    private final TripInviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final TripCollaborationService collaborationService;
    private final TripChatService tripChatService;
    private final EmailWorkerInvoker emailWorkerInvoker;
    private final NotificationService notificationService;

    @ConfigProperty(name = "app.public-url")
    String appPublicUrl;

    @Transactional
    public TripInviteDTO create(UUID tripId, UUID actorId, CreateTripInviteRequest request) {
        Trip trip = requireManageableTrip(tripId, actorId);
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()) {
            throw new BadRequestException("email is required");
        }
        String email = request.getEmail().trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new BadRequestException("email is invalid");
        }

        User actor = userRepository.findById(actorId);
        if (actor != null && email.equalsIgnoreCase(actor.getEmail())) {
            throw new BadRequestException("You cannot invite yourself");
        }

        String permission = normalizePermission(request.getPermission());

        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            User invitee = existingUser.get();
            if (trip.getCreatedBy() != null && trip.getCreatedBy().id.equals(invitee.id)) {
                throw new BadRequestException("Trip creator is already the owner");
            }
            Optional<TripUser> already = tripRepository.findTripUser(trip.id, invitee.id);
            if (already.isPresent()) {
                throw new BadRequestException("User is already a collaborator on this trip");
            }
        }

        inviteRepository
                .findPendingByTripAndEmail(tripId, email)
                .ifPresent(
                        pending -> {
                            pending.setStatus(TripInviteStatus.REVOKED);
                            inviteRepository.persist(pending);
                        });

        TripInvite invite =
                TripInvite.builder()
                        .trip(trip)
                        .email(email)
                        .permissionLevel(permission)
                        .token(generateUniqueToken())
                        .status(TripInviteStatus.PENDING)
                        .invitedBy(actor)
                        .expiresAt(Instant.now().plus(DEFAULT_TTL))
                        .build();
        inviteRepository.persist(invite);

        String tripName = trip.getName() != null ? trip.getName() : "viagem";
        String acceptPath = "/invites/" + invite.getToken();

        if (existingUser.isPresent()) {
            // Usuário já cadastrado: notificação in-app para aceitar no site (sem e-mail duplicado).
            User invitee = existingUser.get();
            notificationService.create(
                    invitee.id,
                    NotificationKind.TRIP_INVITE,
                    "Convite para \"" + tripName + "\"",
                    "Aceite o convite no Baggagi para ver e editar o planejamento.",
                    "TRIP",
                    trip.id,
                    false,
                    acceptPath);
        } else {
            // Usuário novo: e-mail com o endereço convidado + link para ver/editar o planejamento.
            sendInviteEmail(invite, trip);
        }
        log.info(
                "Created trip invite tripId={} email={} existingUser={}",
                tripId,
                email,
                existingUser.isPresent());
        return toDto(invite);
    }

    public List<TripInviteDTO> list(UUID tripId, UUID actorId) {
        Trip trip = requireManageableTrip(tripId, actorId);
        Instant now = Instant.now();
        return inviteRepository.findByTripId(trip.id).stream()
                .map(
                        invite -> {
                            if (invite.getStatus() == TripInviteStatus.PENDING
                                    && invite.getExpiresAt() != null
                                    && invite.getExpiresAt().isBefore(now)) {
                                invite.setStatus(TripInviteStatus.EXPIRED);
                            }
                            return toDto(invite);
                        })
                .toList();
    }

    @Transactional
    public void revoke(UUID tripId, UUID inviteId, UUID actorId) {
        requireManageableTrip(tripId, actorId);
        TripInvite invite =
                inviteRepository
                        .findByIdOptional(inviteId)
                        .filter(i -> i.getTrip() != null && tripId.equals(i.getTrip().id))
                        .orElseThrow(() -> new NotFoundException("Invite not found"));
        if (invite.getStatus() == TripInviteStatus.PENDING) {
            invite.setStatus(TripInviteStatus.REVOKED);
            inviteRepository.persist(invite);
        }
    }

    @Transactional
    public TripInviteDTO resend(UUID tripId, UUID inviteId, UUID actorId) {
        Trip trip = requireManageableTrip(tripId, actorId);
        TripInvite invite =
                inviteRepository
                        .findByIdOptional(inviteId)
                        .filter(i -> i.getTrip() != null && tripId.equals(i.getTrip().id))
                        .orElseThrow(() -> new NotFoundException("Invite not found"));

        if (invite.getStatus() == TripInviteStatus.ACCEPTED) {
            throw new BadRequestException("Invite already accepted");
        }
        if (invite.getStatus() == TripInviteStatus.REVOKED) {
            throw new BadRequestException("Invite was revoked");
        }

        invite.setStatus(TripInviteStatus.PENDING);
        invite.setToken(generateUniqueToken());
        invite.setExpiresAt(Instant.now().plus(DEFAULT_TTL));
        inviteRepository.persist(invite);

        String tripName = trip.getName() != null ? trip.getName() : "viagem";
        String acceptPath = "/invites/" + invite.getToken();
        Optional<User> existingUser = userRepository.findByEmail(invite.getEmail());
        if (existingUser.isPresent()) {
            notificationService.create(
                    existingUser.get().id,
                    NotificationKind.TRIP_INVITE,
                    "Convite para \"" + tripName + "\"",
                    "Aceite o convite no Baggagi para ver e editar o planejamento.",
                    "TRIP",
                    trip.id,
                    false,
                    acceptPath);
        } else {
            sendInviteEmail(invite, trip);
        }
        return toDto(invite);
    }

    @Transactional
    public TripInviteDTO accept(String token, UUID userId) {
        TripInvite invite =
                inviteRepository
                        .findByToken(token)
                        .orElseThrow(() -> new NotFoundException("Invite not found"));

        if (invite.getStatus() == TripInviteStatus.REVOKED) {
            throw new BadRequestException("Invite was revoked");
        }
        if (invite.getStatus() == TripInviteStatus.ACCEPTED) {
            throw new BadRequestException("Invite already accepted");
        }
        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(Instant.now())) {
            invite.setStatus(TripInviteStatus.EXPIRED);
            inviteRepository.persist(invite);
            throw new BadRequestException("Invite expired");
        }

        User user = userRepository.findById(userId);
        if (user == null) {
            throw new NotFoundException("User not found");
        }
        if (user.getEmail() == null
                || !user.getEmail().trim().equalsIgnoreCase(invite.getEmail())) {
            throw new BadRequestException("Logged-in email does not match the invite");
        }

        Trip trip = invite.getTrip();
        if (trip == null) {
            throw new NotFoundException("Trip not found");
        }

        if (trip.getCreatedBy() != null && trip.getCreatedBy().id.equals(user.id)) {
            invite.setStatus(TripInviteStatus.ACCEPTED);
            invite.setAcceptedAt(Instant.now());
            invite.setAcceptedUser(user);
            inviteRepository.persist(invite);
            return toDto(invite);
        }

        Optional<TripUser> existing = tripRepository.findTripUser(trip.id, user.id);
        if (existing.isEmpty()) {
            tripRepository.addTripMember(trip, user, invite.getPermissionLevel());
            trip.setUpdatedAt(Instant.now());
            tripRepository.updateTrip(trip);
            tripChatService.ensureConversationIfEligible(trip.id);
        }

        invite.setStatus(TripInviteStatus.ACCEPTED);
        invite.setAcceptedAt(Instant.now());
        invite.setAcceptedUser(user);
        inviteRepository.persist(invite);
        notifyInviteAccepted(trip, invite, user);
        log.info("Accepted trip invite tripId={} userId={}", trip.id, userId);
        return toDto(invite);
    }

    private void notifyInviteAccepted(Trip trip, TripInvite invite, User acceptedUser) {
        List<UUID> recipients =
                tripRepository.listTripMemberUserIds(trip.id).stream()
                        .filter(id -> !id.equals(acceptedUser.id))
                        .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        if (invite.getInvitedBy() != null && !invite.getInvitedBy().id.equals(acceptedUser.id)) {
            recipients.add(invite.getInvitedBy().id);
        }
        if (recipients.isEmpty()) {
            return;
        }
        String name =
                acceptedUser.getFullName() != null ? acceptedUser.getFullName() : acceptedUser.getEmail();
        String tripName = trip.getName() != null ? trip.getName() : "viagem";
        notificationService.createForUsers(
                recipients,
                NotificationKind.TRIP_SHARED,
                name + " entrou em \"" + tripName + "\"",
                "Convite aceito — novo colaborador na viagem.",
                "TRIP",
                trip.id,
                true);
    }

    private void sendInviteEmail(TripInvite invite, Trip trip) {
        String url = inviteUrl(invite.getToken());
        String tripName = trip.getName() != null ? trip.getName() : "viagem";
        String email = invite.getEmail();
        String subject = "Você foi convidado para \"" + tripName + "\" no Baggagi";
        String text =
                "Olá!\n\n"
                        + "Você ("
                        + email
                        + ") foi convidado para ver e editar o planejamento da viagem \""
                        + tripName
                        + "\" no Baggagi.\n\n"
                        + "Abra o link abaixo para criar sua conta (use este mesmo e-mail) e aceitar o convite "
                        + "(válido por 7 dias):\n"
                        + url
                        + "\n\nSe você não solicitou este acesso, ignore este e-mail.";
        String html =
                "<p>Olá!</p>"
                        + "<p>Você (<strong>"
                        + escapeHtml(email)
                        + "</strong>) foi convidado para <strong>ver e editar</strong> o planejamento da viagem "
                        + "<strong>"
                        + escapeHtml(tripName)
                        + "</strong> no Baggagi.</p>"
                        + "<p><a href=\""
                        + url
                        + "\">Aceitar convite e abrir o planejamento</a></p>"
                        + "<p>O link é válido por 7 dias. Crie a conta com o mesmo e-mail do convite.</p>";
        emailWorkerInvoker.enqueueDirectEmail(email, subject, text, html);
    }

    private Trip requireManageableTrip(UUID tripId, UUID actorId) {
        Trip trip = tripRepository.findById(tripId);
        if (trip == null) {
            throw new NotFoundException("Trip not found");
        }
        collaborationService.requireCanManageMembers(trip, actorId);
        return trip;
    }

    private static String normalizePermission(String permission) {
        if (permission == null || permission.isBlank()) {
            return UserPermissionLevel.VIEWER.name();
        }
        UserPermissionLevel level = UserPermissionLevel.fromString(permission.trim());
        if (level == UserPermissionLevel.OWNER) {
            throw new BadRequestException("Cannot assign OWNER to invited users");
        }
        return level.name();
    }

    private String generateUniqueToken() {
        for (int i = 0; i < 8; i++) {
            String token = randomToken(40);
            if (inviteRepository.findByToken(token).isEmpty()) {
                return token;
            }
        }
        return randomToken(40) + Long.toString(System.currentTimeMillis(), 36);
    }

    private static String randomToken(int length) {
        char[] buf = new char[length];
        for (int i = 0; i < length; i++) {
            buf[i] = TOKEN_ALPHABET[RANDOM.nextInt(TOKEN_ALPHABET.length)];
        }
        return new String(buf);
    }

    private TripInviteDTO toDto(TripInvite invite) {
        TripInviteStatus status = invite.getStatus();
        if (status == TripInviteStatus.PENDING
                && invite.getExpiresAt() != null
                && invite.getExpiresAt().isBefore(Instant.now())) {
            status = TripInviteStatus.EXPIRED;
        }
        return TripInviteDTO.builder()
                .id(invite.id)
                .tripId(invite.getTrip() != null ? invite.getTrip().id : null)
                .email(invite.getEmail())
                .permissionLevel(invite.getPermissionLevel())
                .status(status)
                .expiresAt(invite.getExpiresAt())
                .acceptedAt(invite.getAcceptedAt())
                .createdAt(invite.getCreatedAt())
                .inviteUrl(inviteUrl(invite.getToken()))
                .build();
    }

    private String inviteUrl(String token) {
        String base = appPublicUrl != null ? appPublicUrl.trim() : "";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/invites/" + token;
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
