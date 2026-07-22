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
import org.example.domain.entity.AgencyMember;
import org.example.domain.entity.User;
import org.example.domain.enums.AgencyRole;
import org.example.domain.repository.AgencyMemberRepository;
import org.example.domain.repository.AgencyRepository;
import org.example.domain.repository.B2bTripLogRepository;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.storage.ObjectStorageService;

import java.math.BigDecimal;
import java.text.Normalizer;
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

    @Inject
    AgencyRepository agencyRepository;
    @Inject
    AgencyMemberRepository agencyMemberRepository;
    @Inject
    UserRepository userRepository;
    @Inject
    B2bTripLogRepository auditLogRepository;
    @Inject
    ObjectStorageService objectStorageService;

    public Optional<AgencyMember> requireMembership(UUID userId) {
        List<AgencyMember> memberships = agencyMemberRepository.findAllByUser(userId);
        if (memberships.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(memberships.get(0));
    }

    public AgencyMember requireMembershipOrThrow(UUID userId) {
        return requireMembership(userId)
                .orElseThrow(() -> new NotFoundException("User is not a member of any agency"));
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
                .primaryColor("#000000")
                .planType("B2B_PRO")
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
        agency.setPlanType("B2B_PRO");
        if (stripeSubscriptionId != null && !stripeSubscriptionId.isBlank()) {
            agency.setStripeSubscriptionId(stripeSubscriptionId);
        }
        agencyRepository.persist(agency);
    }

    @Transactional
    public void downgradeSubscription(Agency agency) {
        agency.setPlanType("B2B_FREE");
        agency.setStripeSubscriptionId(null);
        agencyRepository.persist(agency);
    }

    public List<AgencyMemberDTO> listMembers(UUID userId) {
        AgencyMember actor = requireMembershipOrThrow(userId);
        if (actor.getAgencyRole() != AgencyRole.AGENCY_OWNER) {
            throw new ForbiddenException("Only agency owners can list team members");
        }
        return agencyMemberRepository.findAllByAgency(actor.getAgency().id).stream()
                .map(this::toMemberDto)
                .toList();
    }

    @Transactional
    public AgencyMemberDTO inviteMember(UUID actorUserId, InviteAgencyMemberRequest request) {
        AgencyMember actor = requireOwner(actorUserId);
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new BadRequestException("email is required");
        }
        User invitee = userRepository.findByEmail(request.getEmail().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("User not found with email: " + request.getEmail()));
        Optional<AgencyMember> existing =
                agencyMemberRepository.findByAgencyAndUser(actor.getAgency().id, invitee.id);
        if (existing.isPresent()) {
            throw new BadRequestException("User is already a member of this agency");
        }
        AgencyRole role = request.getAgencyRole() != null
                ? request.getAgencyRole()
                : AgencyRole.AGENCY_CONSULTANT;
        if (role == AgencyRole.AGENCY_OWNER) {
            throw new BadRequestException("Cannot invite another OWNER via this endpoint");
        }
        AgencyMember member = AgencyMember.builder()
                .agency(actor.getAgency())
                .user(invitee)
                .agencyRole(role)
                .build();
        agencyMemberRepository.persist(member);
        return toMemberDto(member);
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
                .build();
    }

    /** Branding público (sem markup). */
    public AgencyBrandingDTO toPublicBrandingDto(Agency agency) {
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
