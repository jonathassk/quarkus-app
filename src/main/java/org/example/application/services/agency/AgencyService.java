package org.example.application.services.agency;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.agency.*;
import org.example.domain.entity.Agency;
import org.example.domain.entity.AgencyInvite;
import org.example.domain.entity.AgencyMember;
import org.example.domain.entity.User;
import org.example.domain.enums.AgencyInviteStatus;
import org.example.domain.enums.AgencyRole;
import org.example.domain.repository.AgencyInviteRepository;
import org.example.domain.repository.AgencyMemberRepository;
import org.example.domain.repository.AgencyRepository;
import org.example.domain.repository.B2bTripLogRepository;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.email.EmailWorkerInvoker;
import org.example.infrastructure.storage.ObjectStorageService;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
public class AgencyService {

    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Duration INVITE_TTL = Duration.ofDays(14);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] TOKEN_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

    @Inject
    AgencyRepository agencyRepository;
    @Inject
    AgencyMemberRepository agencyMemberRepository;
    @Inject
    AgencyInviteRepository agencyInviteRepository;
    @Inject
    UserRepository userRepository;
    @Inject
    B2bTripLogRepository auditLogRepository;
    @Inject
    ObjectStorageService objectStorageService;
    @Inject
    EmailWorkerInvoker emailWorkerInvoker;

    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "app.public-url")
    String appPublicUrl;

    public Optional<AgencyMember> requireMembership(UUID userId) {
        List<AgencyMember> memberships = agencyMemberRepository.findAllByUser(userId);
        if (memberships.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(memberships.get(0));
    }

    /**
     * Membership com agência em plano pago ativo ({@code B2B_PRO} e futuros {@code B2B_*}).
     * Agências {@code B2B_INACTIVE}/{@code B2B_FREE} não liberam o portal.
     */
    public Optional<AgencyMember> requireActiveMembership(UUID userId) {
        return requireMembership(userId).filter(m -> isActivePaidPlan(m.getAgency()));
    }

    public AgencyMember requireMembershipOrThrow(UUID userId) {
        return requireActiveMembership(userId)
                .orElseThrow(() -> new NotFoundException("User is not a member of any agency"));
    }

    /** {@code true} se a agência tem assinatura paga ativa (não há plano B2B gratuito). */
    public static boolean isActivePaidPlan(Agency agency) {
        if (agency == null || agency.getPlanType() == null) {
            return false;
        }
        String plan = agency.getPlanType().trim().toUpperCase(Locale.ROOT);
        if (plan.isEmpty() || "B2B_FREE".equals(plan) || "B2B_INACTIVE".equals(plan) || "INACTIVE".equals(plan) || "FREE".equals(plan)) {
            return false;
        }
        return plan.startsWith("B2B_");
    }

    public AgencyMember requireOwner(UUID userId) {
        AgencyMember member = requireMembershipOrThrow(userId);
        if (member.getAgencyRole() != AgencyRole.AGENCY_OWNER) {
            throw new ForbiddenException("Only agency owners can perform this action");
        }
        return member;
    }

    public AgencyBrandingDTO getBrandingForUser(UUID userId) {
        AgencyMember member = requireMembershipOrThrow(userId);
        return toBrandingDto(member.getAgency(), member.getAgencyRole());
    }

    @Transactional
    public AgencyBrandingDTO updateBranding(UUID userId, UpdateAgencyBrandingRequest request) {
        AgencyMember member = requireOwner(userId);
        Agency agency = member.getAgency();

        if (request.getName() != null && !request.getName().isBlank()) {
            agency.setName(request.getName().trim());
        }
        if (request.getPrimaryColor() != null && !request.getPrimaryColor().isBlank()) {
            String color = request.getPrimaryColor().trim();
            if (!HEX_COLOR.matcher(color).matches()) {
                throw new BadRequestException("primaryColor must be a hex color like #FF5500");
            }
            agency.setPrimaryColor(color.toUpperCase(Locale.ROOT));
        }
        if (request.getWhatsappNumber() != null) {
            String wa = request.getWhatsappNumber().trim().replaceAll("[^0-9+]", "");
            agency.setWhatsappNumber(wa.isEmpty() ? null : wa);
        }
        if (request.getMarkupPercentage() != null) {
            BigDecimal markup = request.getMarkupPercentage();
            if (markup.compareTo(BigDecimal.ZERO) < 0 || markup.compareTo(new BigDecimal("999.99")) > 0) {
                throw new BadRequestException("markupPercentage must be between 0 and 999.99");
            }
            agency.setMarkupPercentage(markup);
        }
        agencyRepository.persist(agency);
        return toBrandingDto(agency, member.getAgencyRole());
    }

    @Transactional
    public Agency ensureAgencyForOwner(User owner, String displayName) {
        Optional<Agency> existing = agencyMemberRepository.findPrimaryAgencyForUser(owner.id);
        if (existing.isPresent()) {
            return existing.get();
        }
        String baseName = displayName != null && !displayName.isBlank()
                ? displayName.trim()
                : (owner.getFullName() != null ? owner.getFullName() : "Agência");
        String slug = uniqueSlug(baseName);
        Agency agency = Agency.builder()
                .name(baseName)
                .slug(slug)
                .primaryColor("#134e4a")
                .planType("B2B_INACTIVE")
                .markupPercentage(BigDecimal.ZERO)
                .build();
        agencyRepository.persist(agency);

        AgencyMember member = AgencyMember.builder()
                .agency(agency)
                .user(owner)
                .agencyRole(AgencyRole.AGENCY_OWNER)
                .build();
        agencyMemberRepository.persist(member);
        log.info("Created agency id={} slug={} for user={}", agency.id, slug, owner.id);
        return agency;
    }

    @Transactional
    public void activateSubscription(Agency agency, String stripeSubscriptionId) {
        activateSubscription(agency, stripeSubscriptionId, "B2B_PRO");
    }

    @Transactional
    public void activateSubscription(Agency agency, String stripeSubscriptionId, String planType) {
        String plan = planType != null && !planType.isBlank() ? planType.trim().toUpperCase() : "B2B_PRO";
        agency.setPlanType(plan);
        if (stripeSubscriptionId != null && !stripeSubscriptionId.isBlank()) {
            agency.setStripeSubscriptionId(stripeSubscriptionId);
        }
        agencyRepository.persist(agency);
    }

    /** White-label (logo/cores da agência na proposta) — Solo e Team. Essencial usa marca Baggagi. */
    public static boolean hasWhiteLabel(String planType) {
        if (planType == null || planType.isBlank()) {
            return false;
        }
        String plan = planType.trim().toUpperCase();
        return "B2B_PRO".equals(plan)
                || "B2B_SOLO".equals(plan)
                || "B2B_TEAM".equals(plan);
    }

    public static boolean hasWhiteLabel(Agency agency) {
        return agency != null && hasWhiteLabel(agency.getPlanType());
    }

    @Transactional
    public void downgradeSubscription(Agency agency) {
        agency.setPlanType("B2B_INACTIVE");
        agency.setStripeSubscriptionId(null);
        agencyRepository.persist(agency);
    }

    public AgencyTeamDTO listTeam(UUID userId) {
        AgencyMember actor = requireMembershipOrThrow(userId);
        List<AgencyMemberDTO> members = agencyMemberRepository.findAllByAgency(actor.getAgency().id).stream()
                .map(this::toMemberDto)
                .toList();
        List<AgencyInviteDTO> pending = List.of();
        if (actor.getAgencyRole() == AgencyRole.AGENCY_OWNER) {
            pending = agencyInviteRepository.findPendingByAgency(actor.getAgency().id).stream()
                    .map(this::toInviteDto)
                    .toList();
        }
        return AgencyTeamDTO.builder().members(members).pendingInvites(pending).build();
    }

    /** @deprecated use {@link #listTeam} */
    public List<AgencyMemberDTO> listMembers(UUID userId) {
        return listTeam(userId).getMembers();
    }

    @Transactional
    public InviteAgencyMemberResponse inviteMember(UUID actorUserId, InviteAgencyMemberRequest request) {
        AgencyMember actor = requireOwner(actorUserId);
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new BadRequestException("email is required");
        }
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        AgencyRole role = request.getAgencyRole() != null
                ? request.getAgencyRole()
                : AgencyRole.AGENCY_CONSULTANT;
        if (role == AgencyRole.AGENCY_OWNER) {
            throw new BadRequestException("Cannot invite another OWNER via this endpoint");
        }

        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            User invitee = existingUser.get();
            Optional<AgencyMember> existing =
                    agencyMemberRepository.findByAgencyAndUser(actor.getAgency().id, invitee.id);
            if (existing.isPresent()) {
                throw new BadRequestException("User is already a member of this agency");
            }
            AgencyMember member = AgencyMember.builder()
                    .agency(actor.getAgency())
                    .user(invitee)
                    .agencyRole(role)
                    .build();
            agencyMemberRepository.persist(member);
            return InviteAgencyMemberResponse.builder()
                    .status("ACTIVE")
                    .member(toMemberDto(member))
                    .build();
        }

        agencyInviteRepository.findPendingByAgencyAndEmail(actor.getAgency().id, email)
                .ifPresent(pending -> {
                    pending.setStatus(AgencyInviteStatus.REVOKED);
                    agencyInviteRepository.persist(pending);
                });

        AgencyInvite invite = AgencyInvite.builder()
                .agency(actor.getAgency())
                .email(email)
                .agencyRole(role)
                .token(generateInviteToken())
                .status(AgencyInviteStatus.PENDING)
                .invitedBy(actor.getUser())
                .expiresAt(Instant.now().plus(INVITE_TTL))
                .build();
        agencyInviteRepository.persist(invite);

        String acceptUrl = publicUrl("/settings/team?invite=" + invite.getToken());
        emailWorkerInvoker.enqueueDirectEmail(
                email,
                "Convite para a equipe " + actor.getAgency().getName(),
                "Você foi convidado para a agência " + actor.getAgency().getName()
                        + ". Crie ou acesse sua conta e abra: " + acceptUrl,
                "<p>Você foi convidado para a agência <strong>"
                        + actor.getAgency().getName()
                        + "</strong>.</p><p><a href=\"" + acceptUrl
                        + "\">Aceitar convite</a></p>");

        return InviteAgencyMemberResponse.builder()
                .status("PENDING")
                .invite(toInviteDto(invite))
                .build();
    }

    @Transactional
    public AgencyMemberDTO acceptInvite(UUID userId, String token) {
        AgencyInvite invite = agencyInviteRepository.findByToken(token)
                .orElseThrow(() -> new NotFoundException("Invite not found"));
        if (!invite.isPendingAndValid()) {
            throw new BadRequestException("Invite is no longer valid");
        }
        User user = userRepository.findById(userId);
        if (user == null) {
            throw new NotFoundException("User not found");
        }
        if (user.getEmail() == null
                || !user.getEmail().trim().equalsIgnoreCase(invite.getEmail())) {
            throw new ForbiddenException("Invite email does not match the logged-in user");
        }
        Optional<AgencyMember> existing =
                agencyMemberRepository.findByAgencyAndUser(invite.getAgency().id, userId);
        if (existing.isPresent()) {
            invite.setStatus(AgencyInviteStatus.ACCEPTED);
            invite.setAcceptedAt(Instant.now());
            invite.setAcceptedUser(user);
            agencyInviteRepository.persist(invite);
            return toMemberDto(existing.get());
        }
        AgencyMember member = AgencyMember.builder()
                .agency(invite.getAgency())
                .user(user)
                .agencyRole(invite.getAgencyRole())
                .build();
        agencyMemberRepository.persist(member);
        invite.setStatus(AgencyInviteStatus.ACCEPTED);
        invite.setAcceptedAt(Instant.now());
        invite.setAcceptedUser(user);
        agencyInviteRepository.persist(invite);
        return toMemberDto(member);
    }

    @Transactional
    public void revokeInvite(UUID actorUserId, UUID inviteId) {
        AgencyMember actor = requireOwner(actorUserId);
        AgencyInvite invite = agencyInviteRepository.findById(inviteId);
        if (invite == null || invite.getAgency() == null
                || !invite.getAgency().id.equals(actor.getAgency().id)) {
            throw new NotFoundException("Invite not found");
        }
        invite.setStatus(AgencyInviteStatus.REVOKED);
        agencyInviteRepository.persist(invite);
    }

    private String generateInviteToken() {
        char[] buf = new char[32];
        for (int i = 0; i < buf.length; i++) {
            buf[i] = TOKEN_ALPHABET[RANDOM.nextInt(TOKEN_ALPHABET.length)];
        }
        return new String(buf);
    }

    private String publicUrl(String path) {
        String base = appPublicUrl != null ? appPublicUrl.trim() : "";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    private AgencyInviteDTO toInviteDto(AgencyInvite invite) {
        return AgencyInviteDTO.builder()
                .id(invite.id)
                .email(invite.getEmail())
                .agencyRole(invite.getAgencyRole())
                .status(invite.getStatus())
                .expiresAt(invite.getExpiresAt())
                .createdAt(invite.getCreatedAt())
                .invitedByEmail(invite.getInvitedBy() != null ? invite.getInvitedBy().getEmail() : null)
                .build();
    }

    @Transactional
    public void removeMember(UUID actorUserId, UUID memberUserId) {
        AgencyMember actor = requireOwner(actorUserId);
        if (actor.getUser().id.equals(memberUserId)) {
            throw new BadRequestException("Owner cannot remove themselves");
        }
        AgencyMember target = agencyMemberRepository
                .findByAgencyAndUser(actor.getAgency().id, memberUserId)
                .orElseThrow(() -> new NotFoundException("Member not found"));
        agencyMemberRepository.delete(target);
    }

    public List<B2bAuditLogDTO> listAudit(UUID userId, UUID tripId, int limit) {
        AgencyMember actor = requireMembershipOrThrow(userId);
        int max = Math.min(Math.max(limit, 1), 200);
        var logs = tripId != null
                ? auditLogRepository.findByTrip(tripId).stream()
                        .filter(l -> l.getAgency() != null && l.getAgency().id.equals(actor.getAgency().id))
                        .limit(max)
                        .toList()
                : auditLogRepository.findByAgency(actor.getAgency().id, max);

        if (actor.getAgencyRole() != AgencyRole.AGENCY_OWNER) {
            logs = logs.stream()
                    .filter(l -> l.getActorUser() != null && l.getActorUser().id.equals(userId))
                    .toList();
        }

        return logs.stream().map(l -> B2bAuditLogDTO.builder()
                .id(l.id)
                .tripId(l.getTrip() != null ? l.getTrip().id : null)
                .actorUserId(l.getActorUser() != null ? l.getActorUser().id : null)
                .actorEmail(l.getActorUser() != null ? l.getActorUser().getEmail() : null)
                .actorLabel(l.getActorLabel())
                .action(l.getAction())
                .entityType(l.getEntityType())
                .entityId(l.getEntityId())
                .description(l.getDescription())
                .createdAt(l.getCreatedAt())
                .build()).toList();
    }

    public AgencyBrandingDTO toBrandingDto(Agency agency, AgencyRole role) {
        return AgencyBrandingDTO.builder()
                .id(agency.id)
                .name(agency.getName())
                .slug(agency.getSlug())
                .logoUrl(agency.getLogoUrl())
                .primaryColor(agency.getPrimaryColor())
                .whatsappNumber(agency.getWhatsappNumber())
                .markupPercentage(agency.getMarkupPercentage())
                .planType(agency.getPlanType())
                .agencyRole(role != null ? role.name() : null)
                .whiteLabelEnabled(hasWhiteLabel(agency))
                .build();
    }

    private static final String BAGGAGI_BRAND_NAME = "Baggagi";
    private static final String BAGGAGI_PRIMARY_COLOR = "#134e4a";

    /** Branding público (sem markup). Essencial: marca Baggagi, sem white-label. */
    public AgencyBrandingDTO toPublicBrandingDto(Agency agency) {
        if (!hasWhiteLabel(agency)) {
            return AgencyBrandingDTO.builder()
                    .id(agency.id)
                    .name(BAGGAGI_BRAND_NAME)
                    .slug(agency.getSlug())
                    .logoUrl(null)
                    .primaryColor(BAGGAGI_PRIMARY_COLOR)
                    .whatsappNumber(agency.getWhatsappNumber())
                    .planType(null)
                    .markupPercentage(null)
                    .agencyRole(null)
                    .whiteLabelEnabled(false)
                    .build();
        }
        return AgencyBrandingDTO.builder()
                .id(agency.id)
                .name(agency.getName())
                .slug(agency.getSlug())
                .logoUrl(agency.getLogoUrl())
                .primaryColor(agency.getPrimaryColor())
                .whatsappNumber(agency.getWhatsappNumber())
                .planType(null)
                .markupPercentage(null)
                .agencyRole(null)
                .whiteLabelEnabled(true)
                .build();
    }

    private AgencyMemberDTO toMemberDto(AgencyMember m) {
        User u = m.getUser();
        return AgencyMemberDTO.builder()
                .id(m.id)
                .userId(u != null ? u.id : null)
                .email(u != null ? u.getEmail() : null)
                .fullName(u != null ? u.getFullName() : null)
                .agencyRole(m.getAgencyRole())
                .createdAt(m.getCreatedAt())
                .build();
    }

    private String uniqueSlug(String name) {
        String base = slugify(name);
        if (base.isBlank()) {
            base = "agencia";
        }
        String candidate = base;
        int i = 2;
        while (agencyRepository.findBySlug(candidate).isPresent()) {
            candidate = base + "-" + i++;
        }
        return candidate;
    }

    private static String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return NON_SLUG.matcher(normalized.toLowerCase(Locale.ROOT)).replaceAll("-")
                .replaceAll("^-|-$", "");
    }

    public ObjectStorageService storage() {
        return objectStorageService;
    }

    @Transactional
    public AgencyBrandingDTO confirmLogo(UUID userId, ConfirmAgencyLogoRequest request) {
        AgencyMember member = requireOwner(userId);
        if (request.getS3Key() == null || request.getS3Key().isBlank()) {
            throw new BadRequestException("s3Key is required");
        }
        String expectedPrefix = "agencies/" + member.getAgency().id + "/";
        if (!request.getS3Key().startsWith(expectedPrefix)) {
            throw new ForbiddenException("Invalid logo key for this agency");
        }
        String url = request.getPublicUrl();
        if (url == null || url.isBlank()) {
            url = objectStorageService.getPublicUrl(request.getS3Key());
        }
        Agency agency = member.getAgency();
        agency.setLogoUrl(url);
        agencyRepository.persist(agency);
        return toBrandingDto(agency, member.getAgencyRole());
    }
}
