package org.example.application.services.entitlement;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.entitlement.ConsumeAiGenerationResponse;
import org.example.application.dto.entitlement.EntitlementsDTO;
import org.example.application.exception.EntitlementExceededException;
import org.example.application.services.payment.TripUnlockService;
import org.example.domain.entity.*;
import org.example.domain.enums.AiGenerationKind;
import org.example.domain.enums.TripUnlockKind;
import org.example.domain.enums.UserType;
import org.example.domain.repository.AiGenerationRepository;
import org.example.domain.repository.TripDocumentRepository;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.TripShareLinkRepository;
import org.example.domain.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fonte única de limites B2C/B2B — deriva de {@link UserType}, {@code Workspace.planType}
 * e {@code trip_unlocks}.
 */
@Slf4j
@ApplicationScoped
public class EntitlementService {

    public static final String FEATURE_ACTIVE_TRIPS = "active_trips";
    public static final String FEATURE_AI_GENERATIONS = "ai_generations";
    public static final String FEATURE_DOCUMENTS = "document_bytes";
    public static final String FEATURE_SHARE_LINKS = "share_links";
    public static final String FEATURE_EXPORT_PDF = "export_pdf";

    private static final long MB = 1024L * 1024L;

    @Inject
    UserRepository userRepository;
    @Inject
    TripRepository tripRepository;
    @Inject
    TripDocumentRepository tripDocumentRepository;
    @Inject
    TripShareLinkRepository shareLinkRepository;
    @Inject
    AiGenerationRepository aiGenerationRepository;
    @Inject
    TripUnlockService tripUnlockService;

    public EntitlementsDTO getEntitlements(UUID userId) {
        User user = requireUser(userId);
        Workspace workspace = resolveWorkspace(user);
        PlanLimits limits = resolveLimits(user, workspace);

        long tripsUsed = workspace != null
                ? tripRepository.countActiveByUserAndWorkspace(userId, workspace.id)
                : 0L;
        long aiUsed = aiGenerationRepository.countByUserSince(userId, startOfMonthUtc());
        long docsUsed = workspace != null
                ? tripDocumentRepository.sumSizeBytesByWorkspace(workspace.id)
                : 0L;
        long linksUsed = workspace != null
                ? shareLinkRepository.countActiveByWorkspace(workspace.id)
                : 0L;

        boolean canExport = limits.canExportPdf;
        boolean canAi = limits.maxAiGenerationsPerMonth < 0
                || aiUsed < limits.maxAiGenerationsPerMonth;

        List<String> unlockKinds = new ArrayList<>();
        // Resumo: se o usuário tem algum unlock (via viagens) — o check fino é por tripId.
        if (canExport) {
            unlockKinds.add(TripUnlockKind.EXPORT_PDF.name());
        }
        if (canAi || limits.maxAiGenerationsPerMonth != 0) {
            unlockKinds.add(TripUnlockKind.AI_GENERATIONS.name());
        }

        return EntitlementsDTO.builder()
                .workspaceId(workspace != null ? workspace.id : null)
                .planType(limits.planType)
                .userType(user.getUserType() != null ? user.getUserType().name() : UserType.FREE.name())
                .activeTrips(usage(tripsUsed, limits.maxActiveTrips))
                .aiGenerationsMonth(usage(aiUsed, limits.maxAiGenerationsPerMonth))
                .documentBytes(usage(docsUsed, limits.maxDocumentBytes))
                .shareLinks(usage(linksUsed, limits.maxShareLinks))
                .canExportPdf(canExport)
                .canUseAi(canAi)
                .maxDaysPerPlan(limits.maxDaysPerPlan)
                .maxCitiesPerPlan(limits.maxCitiesPerPlan)
                .tripUnlockKinds(unlockKinds)
                .upgrades(suggestedUpgrades(limits.planType))
                .aiModelTier(resolveAiModelTier(limits.planType))
                .build();
    }

    /** Flash para FREE; Pro para Premium e B2B pago. */
    public static String resolveAiModelTier(String planType) {
        if (planType == null || planType.isBlank()) {
            return "FLASH";
        }
        String p = planType.trim().toUpperCase();
        if ("FREE".equals(p) || "B2B_FREE".equals(p) || "B2B_INACTIVE".equals(p)) {
            return "FLASH";
        }
        if (p.startsWith("B2B_") || "B2C_PREMIUM".equals(p) || "PREMIUM".equals(p)) {
            return "PRO";
        }
        return "FLASH";
    }

    public void requireCanCreateTrip(UUID userId) {
        EntitlementsDTO e = getEntitlements(userId);
        EntitlementsDTO.LimitUsage trips = e.getActiveTrips();
        if (!trips.canConsume(1)) {
            throw exceeded(FEATURE_ACTIVE_TRIPS, e.getPlanType(), trips.getLimit(), trips.getUsed(),
                    "Limite de viagens ativas atingido. Faça upgrade para criar mais.");
        }
    }

    public void requireCanUploadDocument(UUID userId, UUID tripId, long additionalBytes) {
        if (additionalBytes < 0) {
            additionalBytes = 0;
        }
        EntitlementsDTO e = getEntitlements(userId);
        EntitlementsDTO.LimitUsage docs = e.getDocumentBytes();
        if (!docs.canConsume(additionalBytes)) {
            throw exceeded(FEATURE_DOCUMENTS, e.getPlanType(), docs.getLimit(), docs.getUsed(),
                    "Limite de armazenamento de documentos atingido. Faça upgrade ou remova arquivos.");
        }
    }

    public void requireCanCreateShareLink(UUID userId, UUID tripId) {
        EntitlementsDTO e = getEntitlements(userId);
        EntitlementsDTO.LimitUsage links = e.getShareLinks();
        // createOrRotate revoga os ativos da viagem antes de criar 1 novo — só bloqueia
        // se já atingiu o teto do workspace e a viagem ainda não tem link ativo (não vai
        // “liberar” um slot ao rotacionar).
        long activeOnTrip = shareLinkRepository.findActiveByTripId(tripId).size();
        if (activeOnTrip > 0) {
            return; // rotação não aumenta o contador
        }
        if (!links.canConsume(1)) {
            throw exceeded(FEATURE_SHARE_LINKS, e.getPlanType(), links.getLimit(), links.getUsed(),
                    "Limite de links públicos atingido. Faça upgrade para compartilhar mais.");
        }
    }

    public void requireCanExportPdf(UUID userId, UUID tripId) {
        User user = requireUser(userId);
        Workspace workspace = resolveWorkspace(user);
        PlanLimits limits = resolveLimits(user, workspace);
        if (limits.canExportPdf) {
            return;
        }
        if (tripId != null && tripUnlockService.hasUnlock(tripId, TripUnlockKind.EXPORT_PDF)) {
            return;
        }
        EntitlementsDTO e = getEntitlements(userId);
        throw exceeded(FEATURE_EXPORT_PDF, e.getPlanType(), 0, 0,
                "Exportação em PDF não está inclusa no seu plano. Faça upgrade ou desbloqueie esta viagem.");
    }

    @Transactional
    public ConsumeAiGenerationResponse consumeAiGeneration(UUID userId, UUID tripId, AiGenerationKind kind) {
        User user = requireUser(userId);
        Workspace workspace = resolveWorkspace(user);
        PlanLimits limits = resolveLimits(user, workspace);

        long used = aiGenerationRepository.countByUserSince(userId, startOfMonthUtc());
        boolean tripUnlocked = tripId != null
                && tripUnlockService.hasUnlock(tripId, TripUnlockKind.AI_GENERATIONS);

        if (!tripUnlocked && limits.maxAiGenerationsPerMonth >= 0 && used >= limits.maxAiGenerationsPerMonth) {
            throw exceeded(FEATURE_AI_GENERATIONS, limits.planType, limits.maxAiGenerationsPerMonth, used,
                    "Limite mensal de gerações de IA atingido. Faça upgrade ou desbloqueie a viagem.");
        }

        Trip trip = null;
        if (tripId != null) {
            trip = tripRepository.findById(tripId);
            if (trip == null) {
                throw new NotFoundException("Trip not found");
            }
        }

        AiGeneration gen = AiGeneration.builder()
                .user(user)
                .trip(trip)
                .kind(kind != null ? kind : AiGenerationKind.PLAN)
                .build();
        aiGenerationRepository.persist(gen);

        long after = used + 1;
        return ConsumeAiGenerationResponse.builder()
                .generationId(gen.id)
                .used(after)
                .limit(limits.maxAiGenerationsPerMonth)
                .unlimited(limits.maxAiGenerationsPerMonth < 0 || tripUnlocked)
                .aiModelTier(resolveAiModelTier(limits.planType))
                .build();
    }

    public boolean canExportPdfForTrip(UUID userId, UUID tripId) {
        try {
            requireCanExportPdf(userId, tripId);
            return true;
        } catch (EntitlementExceededException e) {
            return false;
        }
    }

    private User requireUser(UUID userId) {
        User user = userRepository.findById(userId);
        if (user == null) {
            throw new NotFoundException("User not found");
        }
        return user;
    }

    private Workspace resolveWorkspace(User user) {
        WorkspaceMember member = WorkspaceMember.find("user", user).firstResult();
        return member != null ? member.getWorkspace() : null;
    }

    private PlanLimits resolveLimits(User user, Workspace workspace) {
        String plan = workspace != null && workspace.getPlanType() != null
                ? workspace.getPlanType().trim().toUpperCase()
                : "FREE";
        UserType userType = user.getUserType() != null ? user.getUserType() : UserType.FREE;

        // Plano do workspace manda; PREMIUM no user reforça B2C quando workspace ainda é FREE.
        if (isPaidB2bPlan(plan)) {
            return PlanLimits.b2bPro(plan);
        }
        if ("B2C_PREMIUM".equals(plan) || userType == UserType.PREMIUM) {
            return PlanLimits.b2cPremium("B2C_PREMIUM".equals(plan) ? plan : "B2C_PREMIUM");
        }
        return PlanLimits.free(plan.isBlank() ? "FREE" : plan);
    }

    private static EntitlementsDTO.LimitUsage usage(long used, long limit) {
        boolean unlimited = limit < 0;
        return EntitlementsDTO.LimitUsage.builder()
                .used(used)
                .limit(unlimited ? -1 : limit)
                .unlimited(unlimited)
                .build();
    }

    private static EntitlementExceededException exceeded(
            String feature, String planType, long limit, long used, String message) {
        return new EntitlementExceededException(feature, planType, limit, used, message);
    }

    private static Instant startOfMonthUtc() {
        LocalDate first = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        return first.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static List<EntitlementsDTO.SuggestedUpgrade> suggestedUpgrades(String planType) {
        List<EntitlementsDTO.SuggestedUpgrade> list = new ArrayList<>();
        if (planType == null
                || "FREE".equalsIgnoreCase(planType)
                || "B2B_FREE".equalsIgnoreCase(planType)
                || "B2B_INACTIVE".equalsIgnoreCase(planType)) {
            list.add(EntitlementsDTO.SuggestedUpgrade.builder()
                    .paymentType("MENSAL")
                    .label("Premium mensal")
                    .description("Mais viagens, IA e export PDF")
                    .build());
            list.add(EntitlementsDTO.SuggestedUpgrade.builder()
                    .paymentType("ANUAL")
                    .label("Premium anual")
                    .description("Mesmos benefícios com desconto")
                    .build());
            list.add(EntitlementsDTO.SuggestedUpgrade.builder()
                    .paymentType("MENSAL_TRIP_AGENT_STARTER")
                    .label("Agência Essencial")
                    .description("Pipeline B2B com marca Baggagi — a partir de R$ 45/mês")
                    .build());
        }
        return list;
    }

    private static boolean isPaidB2bPlan(String plan) {
        if (plan == null || plan.isBlank()) {
            return false;
        }
        String p = plan.trim().toUpperCase();
        if ("B2B_FREE".equals(p) || "B2B_INACTIVE".equals(p)) {
            return false;
        }
        return p.startsWith("B2B_");
    }

    private record PlanLimits(
            String planType,
            long maxActiveTrips,
            long maxAiGenerationsPerMonth,
            long maxDocumentBytes,
            long maxShareLinks,
            boolean canExportPdf,
            int maxDaysPerPlan,
            int maxCitiesPerPlan) {

        /** FREE: 4 planejamentos/mês (= viagens ativas e IA), até 8 dias e 2 cidades. */
        static PlanLimits free(String planType) {
            return new PlanLimits(planType, 4, 4, 50 * MB, 3, false, 8, 2);
        }

        static PlanLimits b2cPremium(String planType) {
            return new PlanLimits(planType, 50, 50, 500 * MB, 100, true, -1, -1);
        }

        static PlanLimits b2bPro(String planType) {
            return new PlanLimits(planType, -1, 200, 2 * 1024 * MB, -1, true, -1, -1);
        }
    }
}
