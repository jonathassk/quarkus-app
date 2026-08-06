package org.example.application.services.proposal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.example.application.dto.proposal.commercial.*;
import org.example.application.services.agency.AgencyService;
import org.example.application.services.proposal.pricing.PricingEngine;
import org.example.domain.entity.*;
import org.example.domain.enums.*;
import org.example.domain.repository.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
public class CommercialProposalService {

    private static final int MAX_OPTIONS = 3;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

    @Inject AgencyService agencyService;
    @Inject UserRepository userRepository;
    @Inject TripRepository tripRepository;
    @Inject AgencyOpportunityRepository opportunityRepository;
    @Inject CommercialProposalRepository proposalRepository;
    @Inject ProposalVersionRepository versionRepository;
    @Inject ProposalOptionRepository optionRepository;
    @Inject ProposalItemRepository itemRepository;
    @Inject ProposalAddOnRepository addOnRepository;
    @Inject ProposalAdjustmentRepository adjustmentRepository;
    @Inject ProposalAcceptanceRepository acceptanceRepository;

    // ─── Create from opportunity ───────────────────────────────────────────

    @Transactional
    public CommercialProposalDTO createFromOpportunity(
            UUID userId, UUID opportunityId, CreateCommercialProposalRequest request) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        AgencyOpportunity opp = opportunityRepository.findById(opportunityId);
        if (opp == null || !opp.getAgency().id.equals(member.getAgency().id)) {
            throw new NotFoundException("Opportunity not found");
        }
        if (opp.getProposal() != null) {
            return toDto(opp.getProposal(), true);
        }

        User creator = userRepository.findById(userId);
        if (creator == null) {
            throw new NotFoundException("User not found");
        }

        Trip trip = opp.getTrip();
        if (trip == null) {
            trip = createTripFromOpportunity(opp, creator, member);
            opp.setTrip(trip);
        }

        ProposalFormat format = request != null && request.getFormat() != null
                ? request.getFormat() : ProposalFormat.SINGLE;
        int markupBps = request != null && request.getDefaultMarkupPercentBps() != null
                ? request.getDefaultMarkupPercentBps()
                : markupBpsFromAgency(member.getAgency());

        CommercialProposal proposal = CommercialProposal.builder()
                .agency(member.getAgency())
                .opportunity(opp)
                .client(opp.getClient())
                .consultant(opp.getAssignedConsultant() != null ? opp.getAssignedConsultant() : creator)
                .shareCode(trip.getShareCode() != null ? trip.getShareCode() : ProposalService.generateShareCode())
                .presentationCurrency(trip.getCurrency() != null ? trip.getCurrency() : "BRL")
                .priceVisibility(PriceVisibility.TOTAL_ONLY)
                .format(format)
                .build();
        proposalRepository.persist(proposal);

        ProposalVersion version = ProposalVersion.builder()
                .proposal(proposal)
                .versionNumber(1)
                .status(CommercialProposalStatus.DRAFT)
                .pricingEditMode(PricingEditMode.QUICK)
                .clientEmail(trip.getProposalClientEmail())
                .clientName(trip.getProposalClientName())
                .options(new ArrayList<>())
                .items(new ArrayList<>())
                .addOns(new ArrayList<>())
                .adjustments(new ArrayList<>())
                .build();
        versionRepository.persist(version);
        proposal.setCurrentVersion(version);

        ProposalOption option = ProposalOption.builder()
                .version(version)
                .trip(trip)
                .position(ProposalOptionPosition.RECOMMENDED)
                .sortOrder(0)
                .recommended(true)
                .name(defaultOptionName(ProposalOptionPosition.RECOMMENDED, trip.getName()))
                .shortDescription(trip.getDescription() != null
                        ? trip.getDescription().substring(0, Math.min(500, trip.getDescription().length()))
                        : null)
                .coverImageUrl(trip.getCoverImageUrl())
                .build();
        optionRepository.persist(option);

        // PACKAGE item in QUICK mode
        long costMinor = trip.getBaseCost() != null ? PricingEngine.toMinor(trip.getBaseCost()) : 0;
        PricingEngine.ItemResult priced = PricingEngine.priceItem(new PricingEngine.ItemInput(
                ItemPricingMode.COST_PLUS,
                costMinor > 0 ? costMinor : null,
                MarkupKind.PERCENT,
                null,
                markupBps,
                null, null, null, null,
                0L,
                trip.getFinalPrice() != null ? PricingEngine.toMinor(trip.getFinalPrice()) : null));
        if (costMinor == 0 && trip.getFinalPrice() != null) {
            priced = PricingEngine.priceItem(new PricingEngine.ItemInput(
                    ItemPricingMode.MANUAL, null, null, null, null,
                    null, null, null, null, 0L, PricingEngine.toMinor(trip.getFinalPrice())));
        }

        ProposalItem packageItem = ProposalItem.builder()
                .version(version)
                .option(option)
                .scope(ProposalItemScope.OPTION)
                .itemType(ProposalItemType.PACKAGE)
                .name("Pacote")
                .pricingMode(costMinor > 0 ? ItemPricingMode.COST_PLUS : ItemPricingMode.MANUAL)
                .costMinor(priced.costMinor() > 0 ? priced.costMinor() : null)
                .markupKind(MarkupKind.PERCENT)
                .markupPercentBps(markupBps)
                .serviceFeeMinor(priced.serviceFeeMinor())
                .clientPriceMinor(priced.clientPriceMinor())
                .expectedRevenueMinor(priced.expectedRevenueMinor())
                .sortOrder(0)
                .build();
        itemRepository.persist(packageItem);
        applyTotalsToOption(option, List.of(priced), List.of());

        syncTripMirror(option);

        opp.setProposal(proposal);
        if (opp.getStage() == OpportunityStage.NEW || opp.getStage() == OpportunityStage.QUALIFYING) {
            opp.setStage(OpportunityStage.QUOTING);
        }
        opportunityRepository.persist(opp);

        if (trip.getShareCode() == null) {
            trip.setShareCode(proposal.getShareCode());
        }
        trip.setProposalStatus(ProposalStatus.QUOTING);

        return toDto(proposal, true);
    }

    private Trip createTripFromOpportunity(AgencyOpportunity opp, User creator, AgencyMember member) {
        Workspace workspace = resolveWorkspace(creator);
        AgencyClient client = opp.getClient();
        LocalDate start = opp.getStartDate() != null ? opp.getStartDate() : LocalDate.now().plusMonths(2);
        int days = opp.getDurationDays() != null && opp.getDurationDays() > 0 ? opp.getDurationDays() : 7;
        LocalDate end = opp.getEndDate() != null ? opp.getEndDate() : start.plusDays(Math.max(days - 1, 0));

        Trip trip = Trip.builder()
                .name(opp.getTitle())
                .description(buildDescription(opp))
                .workspace(workspace)
                .createdBy(creator)
                .agency(member.getAgency())
                .client(client)
                .assignedConsultant(opp.getAssignedConsultant() != null ? opp.getAssignedConsultant() : creator)
                .status(TripStatus.PLANNING)
                .proposalStatus(ProposalStatus.QUOTING)
                .shareCode(ProposalService.generateShareCode())
                .startDate(start)
                .endDate(end)
                .durationDays(days)
                .budgetTotal(opp.getBudgetMax() != null ? opp.getBudgetMax()
                        : opp.getBudgetMin() != null ? opp.getBudgetMin() : BigDecimal.ZERO)
                .currency(opp.getBudgetCurrency() != null ? opp.getBudgetCurrency() : "BRL")
                .proposalClientEmail(client.getEmail())
                .proposalClientName(client.getName())
                .nextFollowUpAt(opp.getNextFollowUpAt())
                .segments(new ArrayList<>())
                .proposalTiers(new ArrayList<>())
                .users(new ArrayList<>())
                .build();
        tripRepository.persist(trip);
        tripRepository.addTripMember(trip, creator, "OWNER");
        return trip;
    }

    // ─── Read ──────────────────────────────────────────────────────────────

    @Transactional
    public CommercialProposalDTO get(UUID userId, UUID proposalId) {
        CommercialProposal proposal = requireAgencyProposal(userId, proposalId);
        return toDto(proposal, true);
    }

    @Transactional
    public CommercialProposalDTO getByTrip(UUID userId, UUID tripId) {
        agencyService.requireMembershipOrThrow(userId);
        ProposalOption option = optionRepository.findByTripId(tripId)
                .orElseThrow(() -> new NotFoundException("Proposal option not found for trip"));
        return toDto(option.getVersion().getProposal(), true);
    }

    // ─── Options ───────────────────────────────────────────────────────────

    @Transactional
    public CommercialProposalDTO duplicateOption(UUID userId, UUID proposalId, UUID optionId) {
        CommercialProposal proposal = requireAgencyProposal(userId, proposalId);
        ProposalVersion version = requireEditableVersion(proposal);
        List<ProposalOption> existing = optionRepository.findByVersionId(version.id);
        long visible = existing.stream().filter(o -> !o.isHidden()).count();
        if (visible >= MAX_OPTIONS) {
            throw new BadRequestException("Máximo de " + MAX_OPTIONS + " opções por proposta");
        }
        ProposalOption source = existing.stream()
                .filter(o -> o.id.equals(optionId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Option not found"));

        User creator = userRepository.findById(userId);
        Trip srcTrip = source.getTrip();
        Trip cloneTrip = Trip.builder()
                .name(srcTrip.getName() + " (cópia)")
                .description(srcTrip.getDescription())
                .workspace(srcTrip.getWorkspace())
                .createdBy(creator)
                .agency(srcTrip.getAgency())
                .client(srcTrip.getClient())
                .assignedConsultant(srcTrip.getAssignedConsultant())
                .status(TripStatus.PLANNING)
                .proposalStatus(ProposalStatus.QUOTING)
                .startDate(srcTrip.getStartDate())
                .endDate(srcTrip.getEndDate())
                .durationDays(srcTrip.getDurationDays())
                .budgetTotal(srcTrip.getBudgetTotal())
                .currency(srcTrip.getCurrency())
                .coverImageUrl(srcTrip.getCoverImageUrl())
                .segments(new ArrayList<>())
                .proposalTiers(new ArrayList<>())
                .users(new ArrayList<>())
                .build();
        tripRepository.persist(cloneTrip);
        tripRepository.addTripMember(cloneTrip, creator, "OWNER");

        ProposalOptionPosition nextPos = nextPosition(existing);
        ProposalOption clone = ProposalOption.builder()
                .version(version)
                .trip(cloneTrip)
                .position(nextPos)
                .sortOrder(existing.size())
                .recommended(false)
                .name(defaultOptionName(nextPos, source.getName()))
                .subtitle(source.getSubtitle())
                .shortDescription(source.getShortDescription())
                .coverImageUrl(source.getCoverImageUrl())
                .includes(source.getIncludes() != null ? new ArrayList<>(source.getIncludes()) : null)
                .excludes(source.getExcludes() != null ? new ArrayList<>(source.getExcludes()) : null)
                .paymentConditions(source.getPaymentConditions())
                .build();
        optionRepository.persist(clone);

        for (ProposalItem item : itemRepository.findByOptionId(source.id)) {
            ProposalItem copy = cloneItem(item, version, clone);
            itemRepository.persist(copy);
        }

        proposal.setFormat(ProposalFormat.COMPARE);
        recalculateOption(clone);
        return toDto(proposal, true);
    }

    @Transactional
    public CommercialProposalDTO updateOption(
            UUID userId, UUID proposalId, UUID optionId, UpsertProposalOptionRequest request) {
        CommercialProposal proposal = requireAgencyProposal(userId, proposalId);
        ProposalVersion version = requireEditableVersion(proposal);
        ProposalOption option = requireOption(version, optionId);

        if (request.getName() != null) option.setName(request.getName().trim());
        if (request.getSubtitle() != null) option.setSubtitle(request.getSubtitle());
        if (request.getShortDescription() != null) option.setShortDescription(request.getShortDescription());
        if (request.getCoverImageUrl() != null) option.setCoverImageUrl(request.getCoverImageUrl());
        if (request.getPosition() != null) option.setPosition(request.getPosition());
        if (request.getIncludes() != null) option.setIncludes(request.getIncludes());
        if (request.getExcludes() != null) option.setExcludes(request.getExcludes());
        if (request.getPaymentConditions() != null) option.setPaymentConditions(request.getPaymentConditions());
        if (request.getSortOrder() != null) option.setSortOrder(request.getSortOrder());
        if (request.getHidden() != null) {
            if (request.getHidden() && optionRepository.countVisibleByVersion(version.id) <= 1 && !option.isHidden()) {
                throw new BadRequestException("É necessário manter pelo menos uma opção visível");
            }
            option.setHidden(request.getHidden());
        }
        if (Boolean.TRUE.equals(request.getRecommended())) {
            for (ProposalOption o : optionRepository.findByVersionId(version.id)) {
                o.setRecommended(o.id.equals(optionId));
            }
        }

        // Quick pricing update
        if (version.getPricingEditMode() == PricingEditMode.QUICK
                && (request.getQuickCostMinor() != null
                || request.getQuickClientPriceMinor() != null
                || request.getQuickMarkupPercentBps() != null
                || request.getQuickServiceFeeMinor() != null)) {
            upsertQuickPackageItem(version, option, request);
            recalculateOption(option);
        }

        syncTripMirror(option);
        return toDto(proposal, true);
    }

    @Transactional
    public CommercialProposalDTO deleteOption(UUID userId, UUID proposalId, UUID optionId) {
        CommercialProposal proposal = requireAgencyProposal(userId, proposalId);
        ProposalVersion version = requireEditableVersion(proposal);
        ProposalOption option = requireOption(version, optionId);
        long visible = optionRepository.countVisibleByVersion(version.id);
        if (!option.isHidden() && visible <= 1) {
            throw new BadRequestException("É necessário manter pelo menos uma opção");
        }
        for (ProposalItem item : itemRepository.findByOptionId(optionId)) {
            itemRepository.delete(item);
        }
        optionRepository.delete(option);
        return toDto(proposal, true);
    }

    // ─── Items ─────────────────────────────────────────────────────────────

    @Transactional
    public CommercialProposalDTO upsertItem(
            UUID userId, UUID proposalId, UpsertProposalItemRequest request) {
        CommercialProposal proposal = requireAgencyProposal(userId, proposalId);
        ProposalVersion version = requireEditableVersion(proposal);
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new BadRequestException("name is required");
        }

        ProposalItemScope scope = request.getScope() != null ? request.getScope() : ProposalItemScope.OPTION;
        ProposalOption option = null;
        if (scope == ProposalItemScope.OPTION) {
            if (request.getOptionId() == null) {
                throw new BadRequestException("optionId is required for OPTION scope");
            }
            option = requireOption(version, request.getOptionId());
        }

        ProposalItem item;
        if (request.getId() != null) {
            item = itemRepository.findById(request.getId());
            if (item == null || !item.getVersion().id.equals(version.id)) {
                throw new NotFoundException("Item not found");
            }
        } else {
            item = ProposalItem.builder().version(version).build();
        }

        item.setScope(scope);
        item.setOption(option);
        item.setItemType(request.getItemType() != null ? request.getItemType() : ProposalItemType.OTHER);
        item.setName(request.getName().trim());
        item.setSubtitle(request.getSubtitle());
        item.setDetails(request.getDetails());
        item.setPricingMode(request.getPricingMode() != null ? request.getPricingMode() : ItemPricingMode.COST_PLUS);
        item.setCostCurrency(request.getCostCurrency());
        item.setCostAmountMinor(request.getCostAmountMinor());
        item.setFxRateMicros(request.getFxRateMicros());
        item.setFxDate(request.getFxDate());
        item.setFxSource(request.getFxSource());
        item.setFxProtectionBps(request.getFxProtectionBps());
        item.setCostMinor(request.getCostMinor());
        item.setMarkupKind(request.getMarkupKind());
        item.setMarkupValueMinor(request.getMarkupValueMinor());
        item.setMarkupPercentBps(request.getMarkupPercentBps());
        item.setSupplierPublicPriceMinor(request.getSupplierPublicPriceMinor());
        item.setCommissionKind(request.getCommissionKind());
        item.setCommissionValueMinor(request.getCommissionValueMinor());
        item.setCommissionPercentBps(request.getCommissionPercentBps());
        item.setServiceFeeMinor(request.getServiceFeeMinor() != null ? request.getServiceFeeMinor() : 0);
        item.setClientPriceMinor(request.getClientPriceMinor());
        item.setSupplierName(request.getSupplierName());
        if (request.getSupplierVisibility() != null) item.setSupplierVisibility(request.getSupplierVisibility());
        if (request.getOptional() != null) item.setOptional(request.getOptional());
        if (request.getHidePrice() != null) item.setHidePrice(request.getHidePrice());
        item.setQuoteExpiresAt(request.getQuoteExpiresAt());
        if (request.getSortOrder() != null) item.setSortOrder(request.getSortOrder());

        applyPriceToItem(item);
        if (item.id == null) {
            itemRepository.persist(item);
        }

        if (version.getPricingEditMode() == PricingEditMode.QUICK
                && item.getItemType() != ProposalItemType.PACKAGE) {
            version.setPricingEditMode(PricingEditMode.DETAILED);
        }

        recalculateAllOptions(version);
        return toDto(proposal, true);
    }

    @Transactional
    public CommercialProposalDTO deleteItem(UUID userId, UUID proposalId, UUID itemId) {
        CommercialProposal proposal = requireAgencyProposal(userId, proposalId);
        ProposalVersion version = requireEditableVersion(proposal);
        ProposalItem item = itemRepository.findById(itemId);
        if (item == null || !item.getVersion().id.equals(version.id)) {
            throw new NotFoundException("Item not found");
        }
        itemRepository.delete(item);
        recalculateAllOptions(version);
        return toDto(proposal, true);
    }

    @Transactional
    public CommercialProposalDTO setPricingMode(
            UUID userId, UUID proposalId, SetPricingModeRequest request) {
        CommercialProposal proposal = requireAgencyProposal(userId, proposalId);
        ProposalVersion version = requireEditableVersion(proposal);
        if (request == null || request.getPricingEditMode() == null) {
            throw new BadRequestException("pricingEditMode is required");
        }
        PricingEditMode mode = request.getPricingEditMode();
        if (mode == PricingEditMode.DETAILED && version.getPricingEditMode() == PricingEditMode.QUICK) {
            // PACKAGE items already exist — DETAILED uses them as source of truth
            version.setPricingEditMode(PricingEditMode.DETAILED);
        } else {
            version.setPricingEditMode(mode);
        }
        recalculateAllOptions(version);
        return toDto(proposal, true);
    }

    // ─── Add-ons & adjustments ─────────────────────────────────────────────

    @Transactional
    public CommercialProposalDTO upsertAddOn(
            UUID userId, UUID proposalId, UpsertProposalAddOnRequest request) {
        CommercialProposal proposal = requireAgencyProposal(userId, proposalId);
        ProposalVersion version = requireEditableVersion(proposal);
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new BadRequestException("name is required");
        }
        ProposalAddOn addOn;
        if (request.getId() != null) {
            addOn = addOnRepository.findById(request.getId());
            if (addOn == null || !addOn.getVersion().id.equals(version.id)) {
                throw new NotFoundException("Add-on not found");
            }
        } else {
            addOn = ProposalAddOn.builder().version(version).build();
        }
        addOn.setName(request.getName().trim());
        addOn.setDescription(request.getDescription());
        addOn.setPriceMinor(request.getPriceMinor() != null ? request.getPriceMinor() : 0);
        addOn.setPricingUnit(request.getPricingUnit() != null ? request.getPricingUnit() : "TOTAL");
        addOn.setQuantityDefault(request.getQuantityDefault() != null ? request.getQuantityDefault() : 1);
        addOn.setEligibleOptionIds(request.getEligibleOptionIds());
        if (request.getRequired() != null) addOn.setRequired(request.getRequired());
        if (request.getOptional() != null) addOn.setOptional(request.getOptional());
        addOn.setExpiresAt(request.getExpiresAt());
        if (request.getSortOrder() != null) addOn.setSortOrder(request.getSortOrder());
        if (addOn.id == null) addOnRepository.persist(addOn);
        return toDto(proposal, true);
    }

    @Transactional
    public CommercialProposalDTO createAdjustment(
            UUID userId, UUID proposalId, CreateAdjustmentRequest request) {
        CommercialProposal proposal = requireAgencyProposal(userId, proposalId);
        ProposalVersion version = requireEditableVersion(proposal);
        Agency agency = proposal.getAgency();
        boolean isDiscount = request != null && request.getAdjustmentType() != null
                && (request.getAdjustmentType() == AdjustmentType.DISCOUNT_PERCENT
                || request.getAdjustmentType() == AdjustmentType.DISCOUNT_FIXED
                || request.getAdjustmentType() == AdjustmentType.COURTESY);
        if (request == null || request.getAdjustmentType() == null) {
            throw new BadRequestException("adjustmentType is required");
        }
        if (agency.isRequireDiscountReason()
                && (request.getReason() == null || request.getReason().isBlank())) {
            throw new BadRequestException("reason is required for adjustments");
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BadRequestException("reason is required");
        }
        ProposalOption option = null;
        long previous = 0;
        if (request.getOptionId() != null) {
            option = requireOption(version, request.getOptionId());
            previous = option.getClientPriceMinor();
        }
        long amount = request.getAmountMinor() != null ? request.getAmountMinor() : 0;
        if (request.getPercentBps() != null && option != null) {
            if (isDiscount && agency.getMaxDiscountBps() != null
                    && request.getPercentBps() > agency.getMaxDiscountBps()) {
                throw new BadRequestException(
                        "Desconto acima do máximo permitido de "
                                + (agency.getMaxDiscountBps() / 100.0) + "%");
            }
            amount = Math.round(option.getClientPriceMinor() * (request.getPercentBps() / 10_000.0));
            if (isDiscount) {
                amount = -Math.abs(amount);
            }
        } else if (isDiscount) {
            amount = -Math.abs(amount);
        }
        User actor = userRepository.findById(userId);
        ProposalAdjustment adj = ProposalAdjustment.builder()
                .version(version)
                .option(option)
                .adjustmentType(request.getAdjustmentType())
                .amountMinor(amount)
                .percentBps(request.getPercentBps())
                .reason(request.getReason().trim())
                .previousClientPriceMinor(previous)
                .createdBy(actor)
                .build();
        adjustmentRepository.persist(adj);
        recalculateAllOptions(version);
        if (option != null) {
            enforceMarginPolicy(agency, version, option, request.getBelowMinimumJustification());
        } else {
            for (ProposalOption o : optionRepository.findByVersionId(version.id)) {
                enforceMarginPolicy(agency, version, o, request.getBelowMinimumJustification());
            }
        }
        return toDto(proposal, true);
    }

    @Transactional
    public CommercialProposalDTO updateSettings(
            UUID userId, UUID proposalId, UpdateProposalSettingsRequest request) {
        CommercialProposal proposal = requireAgencyProposal(userId, proposalId);
        ProposalVersion version = requireEditableVersion(proposal);
        if (request == null) {
            throw new BadRequestException("body is required");
        }
        if (request.getPriceVisibility() != null) {
            proposal.setPriceVisibility(request.getPriceVisibility());
        }
        if (request.getRecommendationNote() != null) {
            version.setRecommendationNote(request.getRecommendationNote().isBlank()
                    ? null : request.getRecommendationNote().trim());
        }
        if (request.getBelowMinimumJustification() != null) {
            version.setBelowMinimumJustification(request.getBelowMinimumJustification().isBlank()
                    ? null : request.getBelowMinimumJustification().trim());
        }
        return toDto(proposal, true);
    }

    @Transactional
    public CommercialProposalDTO convertItemScope(
            UUID userId, UUID proposalId, UUID itemId, ProposalItemScope targetScope, UUID targetOptionId) {
        CommercialProposal proposal = requireAgencyProposal(userId, proposalId);
        ProposalVersion version = requireEditableVersion(proposal);
        ProposalItem item = itemRepository.findById(itemId);
        if (item == null || !item.getVersion().id.equals(version.id)) {
            throw new NotFoundException("Item not found");
        }
        if (targetScope == ProposalItemScope.COMMON) {
            item.setScope(ProposalItemScope.COMMON);
            item.setOption(null);
        } else {
            if (targetOptionId == null) {
                throw new BadRequestException("targetOptionId is required for OPTION scope");
            }
            ProposalOption option = requireOption(version, targetOptionId);
            item.setScope(ProposalItemScope.OPTION);
            item.setOption(option);
        }
        recalculateAllOptions(version);
        return toDto(proposal, true);
    }

    @Transactional
    public CommercialProposalDTO copyItemToOption(
            UUID userId, UUID proposalId, UUID itemId, UUID targetOptionId) {
        CommercialProposal proposal = requireAgencyProposal(userId, proposalId);
        ProposalVersion version = requireEditableVersion(proposal);
        ProposalItem src = itemRepository.findById(itemId);
        if (src == null || !src.getVersion().id.equals(version.id)) {
            throw new NotFoundException("Item not found");
        }
        ProposalOption option = requireOption(version, targetOptionId);
        ProposalItem copy = cloneItem(src, version, option);
        copy.setScope(ProposalItemScope.OPTION);
        itemRepository.persist(copy);
        recalculateAllOptions(version);
        return toDto(proposal, true);
    }

    /**
     * Cliente solicita alteração — status CHANGE_REQUESTED; agente revisa com revise().
     */
    @Transactional
    public PublicCommercialProposalDTO requestChangePublic(
            String shareCode,
            RequestChangeProposalRequest request,
            String clientIp,
            String userAgent) {
        CommercialProposal proposal = proposalRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new NotFoundException("Proposal not found"));
        ProposalVersion version = proposal.getCurrentVersion();
        if (version == null) {
            throw new NotFoundException("Proposal not found");
        }
        assertPublicActionable(version);
        if (request == null || request.getTypes() == null || request.getTypes().isEmpty()) {
            throw new BadRequestException("types is required");
        }
        List<String> types = request.getTypes().stream()
                .map(t -> ChangeRequestType.fromString(t).name())
                .distinct()
                .toList();
        version.setStatus(CommercialProposalStatus.CHANGE_REQUESTED);
        version.setChangeRequestTypes(types);
        version.setChangeRequestMessage(request.getMessage() != null ? request.getMessage().trim() : null);
        version.setChangeRequestedAt(Instant.now());
        version.setChangeRequestedByName(request.getName() != null ? request.getName().trim() : null);
        version.setChangeRequestedByEmail(request.getEmail() != null
                ? request.getEmail().trim().toLowerCase(Locale.ROOT) : null);
        for (ProposalOption opt : optionRepository.findByVersionId(version.id)) {
            opt.getTrip().setProposalStatus(ProposalStatus.NEGOTIATING);
        }
        if (proposal.getOpportunity() != null) {
            proposal.getOpportunity().setStage(OpportunityStage.NEGOTIATING);
        }
        return toPublicDto(proposal, version);
    }

    private void enforceMarginPolicy(
            Agency agency, ProposalVersion version, ProposalOption option, String justification) {
        Integer minBps = agency.getMinMarginBps();
        if (minBps == null || option.getMarginBps() == null) {
            return;
        }
        if (option.getMarginBps() >= minBps) {
            return;
        }
        String msg = "A margem desta proposta está abaixo do mínimo de "
                + String.format(Locale.ROOT, "%.1f", minBps / 100.0) + "%.";
        if (!agency.isAllowBelowMinimum()) {
            throw new BadRequestException(msg + " Ajuste o preço ou solicite aprovação do OWNER.");
        }
        if (justification == null || justification.isBlank()) {
            if (version.getBelowMinimumJustification() == null
                    || version.getBelowMinimumJustification().isBlank()) {
                throw new BadRequestException(msg + " Informe uma justificativa para continuar.");
            }
        } else {
            version.setBelowMinimumJustification(justification.trim());
        }
    }

    // ─── Send / revise ─────────────────────────────────────────────────────

    @Transactional
    public CommercialProposalDTO send(
            UUID userId, UUID proposalId, SendCommercialProposalRequest request) {
        CommercialProposal proposal = requireAgencyProposal(userId, proposalId);
        ProposalVersion version = proposal.getCurrentVersion();
        if (version == null) {
            throw new BadRequestException("Proposal has no version");
        }
        if (version.getStatus() == CommercialProposalStatus.APPROVED) {
            throw new BadRequestException("Proposta aprovada não pode ser reenviada; crie uma revisão");
        }
        if (version.getStatus() == CommercialProposalStatus.SUPERSEDED) {
            throw new BadRequestException("Versão superada");
        }

        String email = request != null && request.getClientEmail() != null
                ? request.getClientEmail().trim()
                : version.getClientEmail();
        if (email == null || email.isBlank()) {
            throw new BadRequestException("clientEmail is required");
        }
        if (request != null && request.getClientName() != null) {
            version.setClientName(request.getClientName().trim());
        }
        version.setClientEmail(email);
        if (request != null && request.getAllowNegotiation() != null) {
            version.setAllowNegotiation(request.getAllowNegotiation());
        }
        if (request != null && request.getExpiresAt() != null) {
            version.setExpiresAt(request.getExpiresAt());
        }
        Agency agency = proposal.getAgency();
        for (ProposalOption opt : optionRepository.findByVersionId(version.id)) {
            if (!opt.isHidden()) {
                enforceMarginPolicy(agency, version, opt, version.getBelowMinimumJustification());
            }
        }
        version.setStatus(CommercialProposalStatus.SENT);
        version.setSentAt(Instant.now());

        for (ProposalOption opt : optionRepository.findByVersionId(version.id)) {
            Trip trip = opt.getTrip();
            trip.setProposalStatus(ProposalStatus.SENT);
            trip.setProposalSentAt(version.getSentAt());
            trip.setProposalExpiresAt(version.getExpiresAt());
            trip.setProposalClientEmail(version.getClientEmail());
            trip.setProposalClientName(version.getClientName());
            trip.setAllowNegotiation(version.isAllowNegotiation());
            trip.setShareCode(proposal.getShareCode());
            syncTripMirror(opt);
        }
        return toDto(proposal, true);
    }

    @Transactional
    public CommercialProposalDTO revise(UUID userId, UUID proposalId) {
        CommercialProposal proposal = requireAgencyProposal(userId, proposalId);
        ProposalVersion old = proposal.getCurrentVersion();
        if (old == null) {
            throw new BadRequestException("Proposal has no version");
        }
        if (old.getStatus() == CommercialProposalStatus.DRAFT) {
            return toDto(proposal, true);
        }
        if (old.getStatus() == CommercialProposalStatus.APPROVED) {
            throw new BadRequestException("Proposta aprovada é imutável");
        }

        old.setStatus(CommercialProposalStatus.SUPERSEDED);
        int next = versionRepository.nextVersionNumber(proposal.id);

        ProposalVersion neu = ProposalVersion.builder()
                .proposal(proposal)
                .versionNumber(next)
                .status(CommercialProposalStatus.DRAFT)
                .pricingEditMode(old.getPricingEditMode())
                .expiresAt(old.getExpiresAt())
                .clientEmail(old.getClientEmail())
                .clientName(old.getClientName())
                .allowNegotiation(old.isAllowNegotiation())
                .recommendationNote(old.getRecommendationNote())
                .options(new ArrayList<>())
                .items(new ArrayList<>())
                .addOns(new ArrayList<>())
                .adjustments(new ArrayList<>())
                .build();
        versionRepository.persist(neu);

        for (ProposalOption src : optionRepository.findByVersionId(old.id)) {
            ProposalOption copy = ProposalOption.builder()
                    .version(neu)
                    .trip(src.getTrip())
                    .position(src.getPosition())
                    .sortOrder(src.getSortOrder())
                    .recommended(src.isRecommended())
                    .hidden(src.isHidden())
                    .name(src.getName())
                    .subtitle(src.getSubtitle())
                    .shortDescription(src.getShortDescription())
                    .coverImageUrl(src.getCoverImageUrl())
                    .includes(src.getIncludes())
                    .excludes(src.getExcludes())
                    .paymentConditions(src.getPaymentConditions())
                    .build();
            optionRepository.persist(copy);
            for (ProposalItem item : itemRepository.findByOptionId(src.id)) {
                itemRepository.persist(cloneItem(item, neu, copy));
            }
            recalculateOption(copy);
            syncTripMirror(copy);
        }
        for (ProposalItem common : itemRepository.findCommonByVersion(old.id)) {
            itemRepository.persist(cloneItem(common, neu, null));
        }
        for (ProposalAddOn addOn : addOnRepository.findByVersionId(old.id)) {
            ProposalAddOn a = ProposalAddOn.builder()
                    .version(neu)
                    .name(addOn.getName())
                    .description(addOn.getDescription())
                    .priceMinor(addOn.getPriceMinor())
                    .pricingUnit(addOn.getPricingUnit())
                    .quantityDefault(addOn.getQuantityDefault())
                    .eligibleOptionIds(addOn.getEligibleOptionIds())
                    .required(addOn.isRequired())
                    .optional(addOn.isOptional())
                    .expiresAt(addOn.getExpiresAt())
                    .sortOrder(addOn.getSortOrder())
                    .build();
            addOnRepository.persist(a);
        }

        proposal.setCurrentVersion(neu);
        for (ProposalOption opt : optionRepository.findByVersionId(neu.id)) {
            opt.getTrip().setProposalStatus(ProposalStatus.QUOTING);
        }
        return toDto(proposal, true);
    }

    // ─── Public ────────────────────────────────────────────────────────────

    @Transactional
    public PublicCommercialProposalDTO getPublic(String shareCode) {
        CommercialProposal proposal = proposalRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new NotFoundException("Proposal not found"));
        ProposalVersion version = proposal.getCurrentVersion();
        if (version == null) {
            throw new NotFoundException("Proposal not found");
        }
        recordView(version);
        return toPublicDto(proposal, version);
    }

    @Transactional
    public PublicCommercialProposalDTO approvePublic(
            String shareCode,
            ApproveCommercialProposalRequest request,
            String clientIp,
            String userAgent) {
        CommercialProposal proposal = proposalRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new NotFoundException("Proposal not found"));
        ProposalVersion version = proposal.getCurrentVersion();
        if (version == null) {
            throw new NotFoundException("Proposal not found");
        }
        assertPublicActionable(version);

        if (request == null || request.getName() == null || request.getName().isBlank()
                || request.getEmail() == null || request.getEmail().isBlank()
                || request.getOptionId() == null) {
            throw new BadRequestException("name, email and optionId are required");
        }
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new BadRequestException("email is invalid");
        }

        ProposalOption option = requireOption(version, request.getOptionId());
        if (option.isHidden()) {
            throw new BadRequestException("Option is not available");
        }

        List<UUID> addonIds = request.getAddonIds() != null ? request.getAddonIds() : List.of();
        long addonTotal = 0;
        for (UUID addonId : addonIds) {
            ProposalAddOn addOn = addOnRepository.findById(addonId);
            if (addOn == null || !addOn.getVersion().id.equals(version.id)) {
                throw new BadRequestException("Invalid add-on");
            }
            if (addOn.getEligibleOptionIds() != null && !addOn.getEligibleOptionIds().isEmpty()
                    && !addOn.getEligibleOptionIds().contains(option.id)) {
                throw new BadRequestException("Add-on not available for selected option");
            }
            addonTotal += addOn.getPriceMinor();
        }
        long totalMinor = option.getClientPriceMinor() + addonTotal;

        // Idempotent: same email+option+version within short window
        List<ProposalAcceptance> existing = acceptanceRepository.findByTripId(option.getTrip().id);
        for (ProposalAcceptance a : existing) {
            if (version.id.equals(a.getVersion() != null ? a.getVersion().id : null)
                    && email.equalsIgnoreCase(a.getEmail())
                    && option.id.equals(a.getOption() != null ? a.getOption().id : null)) {
                return toPublicDto(proposal, version);
            }
        }

        ProposalAcceptance acceptance = ProposalAcceptance.builder()
                .trip(option.getTrip())
                .proposal(proposal)
                .version(version)
                .option(option)
                .addonIds(addonIds)
                .totalMinor(totalMinor)
                .termsText(request.getTermsText())
                .sessionId(request.getSessionId())
                .name(request.getName().trim())
                .email(email)
                .ip(clientIp)
                .userAgent(userAgent)
                .acceptedAt(Instant.now())
                .build();
        acceptanceRepository.persist(acceptance);

        version.setStatus(CommercialProposalStatus.APPROVED);
        Trip trip = option.getTrip();
        trip.setProposalStatus(ProposalStatus.PENDING_PAYMENT);
        trip.setFinalPrice(PricingEngine.toMajor(totalMinor));
        trip.setBaseCost(PricingEngine.toMajor(option.getSupplierCostMinor()));

        if (proposal.getOpportunity() != null) {
            AgencyOpportunity opp = proposal.getOpportunity();
            opp.setStage(OpportunityStage.WON);
            opp.setWonAt(Instant.now());
        }

        return toPublicDto(proposal, version);
    }

    @Transactional
    public PublicCommercialProposalDTO rejectPublic(
            String shareCode, String reason, String clientIp, String userAgent) {
        CommercialProposal proposal = proposalRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new NotFoundException("Proposal not found"));
        ProposalVersion version = proposal.getCurrentVersion();
        assertPublicActionable(version);
        version.setStatus(CommercialProposalStatus.REJECTED);
        version.setRejectReason(reason);
        for (ProposalOption opt : optionRepository.findByVersionId(version.id)) {
            opt.getTrip().setProposalStatus(ProposalStatus.REJECTED);
            opt.getTrip().setProposalRejectReason(reason);
        }
        return toPublicDto(proposal, version);
    }

    public boolean existsByShareCode(String shareCode) {
        return proposalRepository.findByShareCode(shareCode).isPresent();
    }

    // ─── Internals ─────────────────────────────────────────────────────────

    private void upsertQuickPackageItem(
            ProposalVersion version, ProposalOption option, UpsertProposalOptionRequest request) {
        ProposalItem pkg = itemRepository.findByOptionId(option.id).stream()
                .filter(i -> i.getItemType() == ProposalItemType.PACKAGE)
                .findFirst()
                .orElse(null);
        if (pkg == null) {
            pkg = ProposalItem.builder()
                    .version(version)
                    .option(option)
                    .scope(ProposalItemScope.OPTION)
                    .itemType(ProposalItemType.PACKAGE)
                    .name("Pacote")
                    .sortOrder(0)
                    .build();
        }
        ItemPricingMode mode = request.getQuickPricingMode() != null
                ? request.getQuickPricingMode() : ItemPricingMode.COST_PLUS;
        pkg.setPricingMode(mode);
        if (mode == ItemPricingMode.MANUAL) {
            pkg.setClientPriceMinor(request.getQuickClientPriceMinor());
            pkg.setCostMinor(request.getQuickCostMinor());
            pkg.setServiceFeeMinor(request.getQuickServiceFeeMinor() != null ? request.getQuickServiceFeeMinor() : 0);
        } else if (mode == ItemPricingMode.COMMISSION) {
            pkg.setSupplierPublicPriceMinor(request.getQuickClientPriceMinor() != null
                    ? request.getQuickClientPriceMinor() : request.getQuickCostMinor());
            pkg.setCommissionKind(request.getQuickMarkupKind() != null ? request.getQuickMarkupKind() : MarkupKind.PERCENT);
            pkg.setCommissionPercentBps(request.getQuickMarkupPercentBps());
            pkg.setCommissionValueMinor(request.getQuickMarkupValueMinor());
            pkg.setServiceFeeMinor(request.getQuickServiceFeeMinor() != null ? request.getQuickServiceFeeMinor() : 0);
        } else {
            pkg.setCostMinor(request.getQuickCostMinor());
            pkg.setMarkupKind(request.getQuickMarkupKind() != null ? request.getQuickMarkupKind() : MarkupKind.PERCENT);
            pkg.setMarkupPercentBps(request.getQuickMarkupPercentBps());
            pkg.setMarkupValueMinor(request.getQuickMarkupValueMinor());
            pkg.setServiceFeeMinor(request.getQuickServiceFeeMinor() != null ? request.getQuickServiceFeeMinor() : 0);
            if (request.getQuickClientPriceMinor() != null && request.getQuickCostMinor() == null) {
                pkg.setPricingMode(ItemPricingMode.MANUAL);
                pkg.setClientPriceMinor(request.getQuickClientPriceMinor());
            }
        }
        applyPriceToItem(pkg);
        if (pkg.id == null) itemRepository.persist(pkg);
    }

    private void applyPriceToItem(ProposalItem item) {
        PricingEngine.ItemResult r = PricingEngine.priceItem(toInput(item));
        item.setCostMinor(r.costMinor() > 0 || item.getPricingMode() != ItemPricingMode.MANUAL
                ? r.costMinor() : item.getCostMinor());
        if (item.getPricingMode() == ItemPricingMode.MANUAL && item.getCostMinor() == null) {
            // keep null cost
        } else if (item.getPricingMode() != ItemPricingMode.MANUAL || item.getCostMinor() != null) {
            item.setCostMinor(r.costMinor());
        }
        item.setClientPriceMinor(r.clientPriceMinor());
        item.setExpectedRevenueMinor(r.expectedRevenueMinor());
        item.setExpectedCommissionMinor(r.commissionMinor() > 0 ? r.commissionMinor() : null);
        item.setServiceFeeMinor(r.serviceFeeMinor());
    }

    private PricingEngine.ItemInput toInput(ProposalItem item) {
        Long foreign = item.getCostAmountMinor();
        Long fx = item.getFxRateMicros();
        // Se há custo original em outra moeda + câmbio, o motor converte
        Long costForEngine = item.getCostMinor();
        if (foreign != null && fx != null && fx > 0
                && (costForEngine == null || costForEngine == 0)) {
            costForEngine = null; // deixa o motor resolver via FX
        }
        return new PricingEngine.ItemInput(
                item.getPricingMode(),
                costForEngine,
                item.getMarkupKind(),
                item.getMarkupValueMinor(),
                item.getMarkupPercentBps(),
                item.getSupplierPublicPriceMinor(),
                item.getCommissionKind(),
                item.getCommissionValueMinor(),
                item.getCommissionPercentBps(),
                item.getServiceFeeMinor(),
                item.getClientPriceMinor(),
                foreign,
                fx,
                item.getFxProtectionBps());
    }

    private void recalculateAllOptions(ProposalVersion version) {
        for (ProposalOption option : optionRepository.findByVersionId(version.id)) {
            recalculateOption(option);
            syncTripMirror(option);
        }
    }

    private void recalculateOption(ProposalOption option) {
        ProposalVersion version = option.getVersion();
        List<ProposalItem> items = itemRepository.findForOptionPricing(version.id, option.id);
        List<PricingEngine.ItemResult> results = items.stream()
                .map(i -> {
                    applyPriceToItem(i);
                    return PricingEngine.priceItem(toInput(i));
                })
                .collect(Collectors.toList());
        List<ProposalAdjustment> adjs = adjustmentRepository.findForOption(version.id, option.id);
        PricingEngine.OptionTotals totals = PricingEngine.sumItems(results);
        for (ProposalAdjustment adj : adjs) {
            totals = PricingEngine.applyAdjustment(totals, adj.getAmountMinor());
        }
        applyTotalsToOption(option, results, adjs);
        option.setSupplierCostMinor(totals.supplierCostMinor());
        option.setMarkupAmountMinor(totals.markupAmountMinor());
        option.setServiceFeeMinor(totals.serviceFeeMinor());
        option.setCommissionMinor(totals.commissionMinor());
        option.setClientPriceMinor(totals.clientPriceMinor());
        option.setExpectedRevenueMinor(totals.expectedRevenueMinor());
        option.setMarginBps(totals.marginBps());
    }

    private void applyTotalsToOption(
            ProposalOption option,
            List<PricingEngine.ItemResult> results,
            List<ProposalAdjustment> adjs) {
        PricingEngine.OptionTotals totals = PricingEngine.sumItems(results);
        for (ProposalAdjustment adj : adjs) {
            totals = PricingEngine.applyAdjustment(totals, adj.getAmountMinor());
        }
        option.setSupplierCostMinor(totals.supplierCostMinor());
        option.setMarkupAmountMinor(totals.markupAmountMinor());
        option.setServiceFeeMinor(totals.serviceFeeMinor());
        option.setCommissionMinor(totals.commissionMinor());
        option.setClientPriceMinor(totals.clientPriceMinor());
        option.setExpectedRevenueMinor(totals.expectedRevenueMinor());
        option.setMarginBps(totals.marginBps());
    }

    private void syncTripMirror(ProposalOption option) {
        Trip trip = option.getTrip();
        if (trip == null) return;
        trip.setBaseCost(PricingEngine.toMajor(option.getSupplierCostMinor()));
        trip.setFinalPrice(PricingEngine.toMajor(option.getClientPriceMinor()));
        if (option.isRecommended() || trip.getName() == null) {
            // keep trip name as option name for pipeline
        }
    }

    private ProposalItem cloneItem(ProposalItem src, ProposalVersion version, ProposalOption option) {
        return ProposalItem.builder()
                .version(version)
                .option(option)
                .scope(src.getScope())
                .itemType(src.getItemType())
                .name(src.getName())
                .subtitle(src.getSubtitle())
                .details(src.getDetails())
                .pricingMode(src.getPricingMode())
                .costCurrency(src.getCostCurrency())
                .costAmountMinor(src.getCostAmountMinor())
                .fxRateMicros(src.getFxRateMicros())
                .fxDate(src.getFxDate())
                .fxSource(src.getFxSource())
                .fxProtectionBps(src.getFxProtectionBps())
                .costMinor(src.getCostMinor())
                .markupKind(src.getMarkupKind())
                .markupValueMinor(src.getMarkupValueMinor())
                .markupPercentBps(src.getMarkupPercentBps())
                .supplierPublicPriceMinor(src.getSupplierPublicPriceMinor())
                .commissionKind(src.getCommissionKind())
                .commissionValueMinor(src.getCommissionValueMinor())
                .commissionPercentBps(src.getCommissionPercentBps())
                .serviceFeeMinor(src.getServiceFeeMinor())
                .clientPriceMinor(src.getClientPriceMinor())
                .expectedCommissionMinor(src.getExpectedCommissionMinor())
                .expectedRevenueMinor(src.getExpectedRevenueMinor())
                .supplierName(src.getSupplierName())
                .supplierVisibility(src.getSupplierVisibility())
                .optional(src.isOptional())
                .hidePrice(src.isHidePrice())
                .quoteExpiresAt(src.getQuoteExpiresAt())
                .sortOrder(src.getSortOrder())
                .build();
    }

    private void recordView(ProposalVersion version) {
        Instant now = Instant.now();
        Instant last = version.getLastViewedAt();
        if (last != null && Duration.between(last, now).getSeconds() < 45) {
            return;
        }
        version.setLastViewedAt(now);
        version.setViewCount(version.getViewCount() + 1);
        if (version.getStatus() == CommercialProposalStatus.SENT) {
            version.setStatus(CommercialProposalStatus.VIEWED);
        }
        for (ProposalOption opt : optionRepository.findByVersionId(version.id)) {
            Trip trip = opt.getTrip();
            trip.setProposalLastViewedAt(now);
            trip.setProposalViewCount((trip.getProposalViewCount() == null ? 0 : trip.getProposalViewCount()) + 1);
        }
    }

    private void assertPublicActionable(ProposalVersion version) {
        if (version.getStatus() == CommercialProposalStatus.APPROVED) {
            throw new BadRequestException("Proposal already approved");
        }
        if (version.getStatus() == CommercialProposalStatus.REJECTED
                || version.getStatus() == CommercialProposalStatus.SUPERSEDED
                || version.getStatus() == CommercialProposalStatus.EXPIRED) {
            throw new BadRequestException("Proposal is not actionable");
        }
        if (version.getStatus() == CommercialProposalStatus.DRAFT) {
            throw new BadRequestException("Proposal not sent");
        }
        if (version.isExpired()) {
            version.setStatus(CommercialProposalStatus.EXPIRED);
            throw new BadRequestException("Proposal expired");
        }
    }

    private CommercialProposal requireAgencyProposal(UUID userId, UUID proposalId) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        CommercialProposal proposal = proposalRepository.findById(proposalId);
        if (proposal == null || !proposal.getAgency().id.equals(member.getAgency().id)) {
            throw new NotFoundException("Proposal not found");
        }
        return proposal;
    }

    private ProposalVersion requireEditableVersion(CommercialProposal proposal) {
        ProposalVersion version = proposal.getCurrentVersion();
        if (version == null) {
            throw new BadRequestException("Proposal has no version");
        }
        if (!version.getStatus().isEditable()) {
            throw new BadRequestException("Versão enviada está bloqueada; use revise para criar nova versão");
        }
        return version;
    }

    private ProposalOption requireOption(ProposalVersion version, UUID optionId) {
        return optionRepository.findByVersionId(version.id).stream()
                .filter(o -> o.id.equals(optionId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Option not found"));
    }

    private ProposalOptionPosition nextPosition(List<ProposalOption> existing) {
        boolean hasEssential = existing.stream().anyMatch(o -> o.getPosition() == ProposalOptionPosition.ESSENTIAL);
        boolean hasPremium = existing.stream().anyMatch(o -> o.getPosition() == ProposalOptionPosition.PREMIUM);
        if (!hasEssential) return ProposalOptionPosition.ESSENTIAL;
        if (!hasPremium) return ProposalOptionPosition.PREMIUM;
        return ProposalOptionPosition.CUSTOM;
    }

    private String defaultOptionName(ProposalOptionPosition pos, String fallback) {
        return switch (pos) {
            case ESSENTIAL -> "Essencial";
            case RECOMMENDED -> "Recomendada";
            case PREMIUM -> "Premium";
            default -> fallback != null ? fallback : "Opção";
        };
    }

    private int markupBpsFromAgency(Agency agency) {
        if (agency.getMarkupPercentage() == null) return 1500;
        return agency.getMarkupPercentage()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .intValue();
    }

    private Workspace resolveWorkspace(User creator) {
        WorkspaceMember member = WorkspaceMember.find("user", creator).firstResult();
        if (member != null) {
            return member.getWorkspace();
        }
        Workspace workspace = Workspace.builder()
                .name("Workspace Pessoal de " + (creator.getFullName() != null
                        ? creator.getFullName()
                        : creator.getUsername()))
                .planType("FREE")
                .primaryColor("#000000")
                .build();
        workspace.persist();
        WorkspaceMember wm = WorkspaceMember.builder()
                .workspace(workspace)
                .user(creator)
                .role(WorkspaceRole.OWNER)
                .build();
        wm.persist();
        return workspace;
    }

    private String buildDescription(AgencyOpportunity opp) {
        StringBuilder sb = new StringBuilder();
        if (opp.getRequestSummary() != null) sb.append(opp.getRequestSummary());
        if (opp.getDestinations() != null) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append("Destino: ").append(opp.getDestinations());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    // ─── Mapping ───────────────────────────────────────────────────────────

    public CommercialProposalDTO toDto(CommercialProposal proposal, boolean includeInternal) {
        ProposalVersion version = proposal.getCurrentVersion();
        return CommercialProposalDTO.builder()
                .id(proposal.id)
                .agencyId(proposal.getAgency().id)
                .opportunityId(proposal.getOpportunity() != null ? proposal.getOpportunity().id : null)
                .clientId(proposal.getClient() != null ? proposal.getClient().id : null)
                .clientName(proposal.getClient() != null ? proposal.getClient().getName() : null)
                .consultantId(proposal.getConsultant() != null ? proposal.getConsultant().id : null)
                .shareCode(proposal.getShareCode())
                .presentationCurrency(proposal.getPresentationCurrency())
                .priceVisibility(proposal.getPriceVisibility())
                .format(proposal.getFormat())
                .currentVersionId(version != null ? version.id : null)
                .currentVersion(version != null ? toVersionDto(version, includeInternal) : null)
                .createdAt(proposal.getCreatedAt())
                .updatedAt(proposal.getUpdatedAt())
                .build();
    }

    private CommercialProposalVersionDTO toVersionDto(ProposalVersion version, boolean includeInternal) {
        List<ProposalOption> options = optionRepository.findByVersionId(version.id);
        List<CommercialProposalOptionDTO> optionDtos = options.stream()
                .map(o -> toOptionDto(o, includeInternal))
                .collect(Collectors.toList());
        return CommercialProposalVersionDTO.builder()
                .id(version.id)
                .versionNumber(version.getVersionNumber())
                .status(version.getStatus())
                .pricingEditMode(version.getPricingEditMode())
                .expiresAt(version.getExpiresAt())
                .clientEmail(version.getClientEmail())
                .clientName(version.getClientName())
                .allowNegotiation(version.isAllowNegotiation())
                .recommendationNote(version.getRecommendationNote())
                .sentAt(version.getSentAt())
                .lastViewedAt(version.getLastViewedAt())
                .viewCount(version.getViewCount())
                .rejectReason(version.getRejectReason())
                .changeRequestTypes(version.getChangeRequestTypes())
                .changeRequestMessage(version.getChangeRequestMessage())
                .changeRequestedAt(version.getChangeRequestedAt())
                .changeRequestedByName(version.getChangeRequestedByName())
                .changeRequestedByEmail(version.getChangeRequestedByEmail())
                .belowMinimumJustification(version.getBelowMinimumJustification())
                .agencyMinMarginBps(version.getProposal() != null && version.getProposal().getAgency() != null
                        ? version.getProposal().getAgency().getMinMarginBps() : null)
                .options(optionDtos)
                .commonItems(itemRepository.findCommonByVersion(version.id).stream()
                        .map(this::toItemDto)
                        .collect(Collectors.toList()))
                .addOns(addOnRepository.findByVersionId(version.id).stream()
                        .map(this::toAddOnDto)
                        .collect(Collectors.toList()))
                .adjustments(includeInternal
                        ? adjustmentRepository.findByVersionId(version.id).stream()
                            .map(this::toAdjDto).collect(Collectors.toList())
                        : List.of())
                .build();
    }

    private CommercialProposalOptionDTO toOptionDto(ProposalOption option, boolean includeInternal) {
        List<CommercialProposalItemDTO> items = includeInternal
                ? itemRepository.findByOptionId(option.id).stream().map(this::toItemDto).collect(Collectors.toList())
                : List.of();
        FinancialSummaryDTO summary = includeInternal
                ? FinancialSummaryDTO.builder()
                    .supplierCostMinor(option.getSupplierCostMinor())
                    .markupAmountMinor(option.getMarkupAmountMinor())
                    .serviceFeeMinor(option.getServiceFeeMinor())
                    .commissionMinor(option.getCommissionMinor())
                    .clientPriceMinor(option.getClientPriceMinor())
                    .expectedRevenueMinor(option.getExpectedRevenueMinor())
                    .marginBps(option.getMarginBps())
                    .markupBps(PricingEngine.markupBps(option.getSupplierCostMinor(), option.getMarkupAmountMinor()))
                    .build()
                : null;
        String marginWarning = null;
        if (includeInternal) {
            Agency agency = option.getVersion().getProposal().getAgency();
            Integer min = agency != null ? agency.getMinMarginBps() : null;
            if (min != null && option.getMarginBps() != null && option.getMarginBps() < min) {
                marginWarning = "A margem desta proposta está abaixo do mínimo de "
                        + String.format(Locale.ROOT, "%.1f", min / 100.0) + "%.";
            }
        }
        return CommercialProposalOptionDTO.builder()
                .id(option.id)
                .tripId(option.getTrip().id)
                .position(option.getPosition())
                .sortOrder(option.getSortOrder())
                .recommended(option.isRecommended())
                .hidden(option.isHidden())
                .name(option.getName())
                .subtitle(option.getSubtitle())
                .shortDescription(option.getShortDescription())
                .coverImageUrl(option.getCoverImageUrl())
                .includes(option.getIncludes())
                .excludes(option.getExcludes())
                .paymentConditions(option.getPaymentConditions())
                .supplierCostMinor(includeInternal ? option.getSupplierCostMinor() : 0)
                .markupAmountMinor(includeInternal ? option.getMarkupAmountMinor() : 0)
                .serviceFeeMinor(includeInternal ? option.getServiceFeeMinor() : 0)
                .commissionMinor(includeInternal ? option.getCommissionMinor() : 0)
                .clientPriceMinor(option.getClientPriceMinor())
                .expectedRevenueMinor(includeInternal ? option.getExpectedRevenueMinor() : 0)
                .marginBps(includeInternal ? option.getMarginBps() : null)
                .markupBps(includeInternal
                        ? PricingEngine.markupBps(option.getSupplierCostMinor(), option.getMarkupAmountMinor())
                        : null)
                .marginWarning(marginWarning)
                .items(items)
                .financialSummary(summary)
                .build();
    }

    private CommercialProposalItemDTO toItemDto(ProposalItem item) {
        PricingEngine.ItemResult r = PricingEngine.priceItem(toInput(item));
        return CommercialProposalItemDTO.builder()
                .id(item.id)
                .optionId(item.getOption() != null ? item.getOption().id : null)
                .scope(item.getScope())
                .itemType(item.getItemType())
                .name(item.getName())
                .subtitle(item.getSubtitle())
                .details(item.getDetails())
                .pricingMode(item.getPricingMode())
                .costCurrency(item.getCostCurrency())
                .costAmountMinor(item.getCostAmountMinor())
                .fxRateMicros(item.getFxRateMicros())
                .fxDate(item.getFxDate())
                .fxSource(item.getFxSource())
                .fxProtectionBps(item.getFxProtectionBps())
                .costMinor(item.getCostMinor())
                .markupKind(item.getMarkupKind())
                .markupValueMinor(item.getMarkupValueMinor())
                .markupPercentBps(item.getMarkupPercentBps())
                .supplierPublicPriceMinor(item.getSupplierPublicPriceMinor())
                .commissionKind(item.getCommissionKind())
                .commissionValueMinor(item.getCommissionValueMinor())
                .commissionPercentBps(item.getCommissionPercentBps())
                .serviceFeeMinor(item.getServiceFeeMinor())
                .clientPriceMinor(item.getClientPriceMinor())
                .expectedCommissionMinor(item.getExpectedCommissionMinor())
                .expectedRevenueMinor(item.getExpectedRevenueMinor())
                .marginBps(r.marginBps())
                .markupBps(r.markupBps())
                .supplierName(item.getSupplierName())
                .supplierVisibility(item.getSupplierVisibility())
                .optional(item.isOptional())
                .hidePrice(item.isHidePrice())
                .quoteExpiresAt(item.getQuoteExpiresAt())
                .sortOrder(item.getSortOrder())
                .build();
    }

    private CommercialProposalAddOnDTO toAddOnDto(ProposalAddOn a) {
        return CommercialProposalAddOnDTO.builder()
                .id(a.id)
                .name(a.getName())
                .description(a.getDescription())
                .priceMinor(a.getPriceMinor())
                .pricingUnit(a.getPricingUnit())
                .quantityDefault(a.getQuantityDefault())
                .eligibleOptionIds(a.getEligibleOptionIds())
                .required(a.isRequired())
                .optional(a.isOptional())
                .expiresAt(a.getExpiresAt())
                .sortOrder(a.getSortOrder())
                .build();
    }

    private CommercialProposalAdjustmentDTO toAdjDto(ProposalAdjustment a) {
        return CommercialProposalAdjustmentDTO.builder()
                .id(a.id)
                .optionId(a.getOption() != null ? a.getOption().id : null)
                .adjustmentType(a.getAdjustmentType())
                .amountMinor(a.getAmountMinor())
                .percentBps(a.getPercentBps())
                .reason(a.getReason())
                .previousClientPriceMinor(a.getPreviousClientPriceMinor())
                .createdBy(a.getCreatedBy() != null ? a.getCreatedBy().id : null)
                .createdAt(a.getCreatedAt())
                .build();
    }

    private PublicCommercialProposalDTO toPublicDto(CommercialProposal proposal, ProposalVersion version) {
        Agency agency = proposal.getAgency();
        boolean powered = agency.getPlanType() != null
                && "B2B_STARTER".equalsIgnoreCase(agency.getPlanType());
        List<PublicOptionDTO> options = optionRepository.findByVersionId(version.id).stream()
                .filter(o -> !o.isHidden())
                .map(o -> {
                    List<PublicItemDTO> items = itemRepository.findForOptionPricing(version.id, o.id).stream()
                            .filter(i -> !i.isHidePrice() || proposal.getPriceVisibility() != PriceVisibility.TOTAL_ONLY)
                            .map(i -> PublicItemDTO.builder()
                                    .id(i.id)
                                    .name(i.getName())
                                    .subtitle(i.getSubtitle())
                                    .itemType(i.getItemType().name())
                                    .clientPriceMinor(
                                            proposal.getPriceVisibility() == PriceVisibility.TOTAL_ONLY
                                                    || i.isHidePrice() ? null : i.getClientPriceMinor())
                                    .hidePrice(i.isHidePrice())
                                    .optional(i.isOptional())
                                    .supplierDisplay(publicSupplier(i))
                                    .build())
                            .collect(Collectors.toList());
                    return PublicOptionDTO.builder()
                            .id(o.id)
                            .name(o.getName())
                            .subtitle(o.getSubtitle())
                            .shortDescription(o.getShortDescription())
                            .coverImageUrl(o.getCoverImageUrl())
                            .recommended(o.isRecommended())
                            .includes(o.getIncludes())
                            .excludes(o.getExcludes())
                            .paymentConditions(o.getPaymentConditions())
                            .clientPriceMinor(o.getClientPriceMinor())
                            .items(proposal.getPriceVisibility() == PriceVisibility.TOTAL_ONLY
                                    ? List.of() : items)
                            .build();
                })
                .collect(Collectors.toList());

        return PublicCommercialProposalDTO.builder()
                .proposalId(proposal.id)
                .versionId(version.id)
                .versionNumber(version.getVersionNumber())
                .status(version.getStatus().name())
                .currency(proposal.getPresentationCurrency())
                .priceVisibility(proposal.getPriceVisibility())
                .expiresAt(version.getExpiresAt())
                .expired(version.isExpired())
                .recommendationNote(version.getRecommendationNote())
                .agencyName(agency.getName())
                .agencyLogoUrl(agency.getLogoUrl())
                .agencyPrimaryColor(agency.getPrimaryColor())
                .poweredByBaggagi(powered)
                .clientName(version.getClientName())
                .options(options)
                .changeRequestAllowed(
                        version.getStatus() == CommercialProposalStatus.SENT
                                || version.getStatus() == CommercialProposalStatus.VIEWED)
                .addOns(addOnRepository.findByVersionId(version.id).stream()
                        .map(a -> PublicAddOnDTO.builder()
                                .id(a.id)
                                .name(a.getName())
                                .description(a.getDescription())
                                .priceMinor(a.getPriceMinor())
                                .pricingUnit(a.getPricingUnit())
                                .eligibleOptionIds(a.getEligibleOptionIds())
                                .required(a.isRequired())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    private String publicSupplier(ProposalItem i) {
        if (i.getSupplierVisibility() == SupplierVisibility.HIDE_UNTIL_APPROVAL) {
            return null;
        }
        if (i.getSupplierVisibility() == SupplierVisibility.DESCRIPTION_ONLY) {
            return i.getSubtitle();
        }
        return i.getSupplierName();
    }
}
