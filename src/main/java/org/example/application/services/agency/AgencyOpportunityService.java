package org.example.application.services.agency;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.example.application.dto.agency.AddOpportunityActivityRequest;
import org.example.application.dto.agency.AgencyOpportunityActivityDTO;
import org.example.application.dto.agency.AgencyOpportunityDTO;
import org.example.application.dto.agency.AgencyOpportunityFileDTO;
import org.example.application.dto.agency.AgencyOpportunityTaskDTO;
import org.example.application.dto.agency.DuplicateContactCheckResponse;
import org.example.application.dto.agency.MarkOpportunityLostRequest;
import org.example.application.dto.agency.UpsertAgencyOpportunityRequest;
import org.example.application.dto.agency.UpsertOpportunityTaskRequest;
import org.example.application.services.proposal.ProposalService;
import org.example.domain.entity.Agency;
import org.example.domain.entity.AgencyClient;
import org.example.domain.entity.AgencyMember;
import org.example.domain.entity.AgencyOpportunity;
import org.example.domain.entity.AgencyOpportunityActivity;
import org.example.domain.entity.AgencyOpportunityFile;
import org.example.domain.entity.AgencyOpportunityTask;
import org.example.domain.entity.Trip;
import org.example.domain.entity.User;
import org.example.domain.entity.Workspace;
import org.example.domain.entity.WorkspaceMember;
import org.example.domain.enums.ContactStatus;
import org.example.domain.enums.AgencyRole;
import org.example.domain.enums.OpportunityActivityType;
import org.example.domain.enums.OpportunityFileKind;
import org.example.domain.enums.OpportunityLostReasonCode;
import org.example.domain.enums.OpportunityNextActionType;
import org.example.domain.enums.OpportunityPriority;
import org.example.domain.enums.OpportunityStage;
import org.example.domain.enums.OpportunityTaskStatus;
import org.example.domain.enums.ProposalStatus;
import org.example.domain.enums.QualificationStatus;
import org.example.domain.enums.TripStatus;
import org.example.domain.enums.WorkspaceRole;
import org.example.domain.repository.AgencyClientRepository;
import org.example.domain.repository.AgencyOpportunityRepository;
import org.example.domain.repository.AgencyOpportunityActivityRepository;
import org.example.domain.repository.AgencyOpportunityFileRepository;
import org.example.domain.repository.AgencyOpportunityTaskRepository;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.storage.ObjectStorageService;
import org.example.utils.DocumentUploadSupport;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
public class AgencyOpportunityService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s.]+\\.[^@\\s]+$");

    private static final List<String> VALID_LEAD_SOURCES = List.of(
            "RETURNING", "REFERRAL", "WHATSAPP", "INSTAGRAM", "SITE", "GOOGLE",
            "EVENT", "PARTNER_HOTEL", "PARTNER_AGENCY", "ADS", "PHONE", "WALK_IN", "OTHER");

    @Inject
    AgencyService agencyService;
    @Inject
    AgencyOpportunityRepository opportunityRepository;
    @Inject
    AgencyOpportunityActivityRepository activityRepository;
    @Inject
    AgencyClientRepository clientRepository;
    @Inject
    TripRepository tripRepository;
    @Inject
    UserRepository userRepository;
    @Inject
    AgencyOpportunityTaskRepository taskRepository;
    @Inject
    AgencyOpportunityFileRepository fileRepository;
    @Inject
    ObjectStorageService objectStorageService;

    public List<AgencyOpportunityDTO> list(
            UUID userId,
            String stage,
            UUID consultantId,
            UUID clientId,
            String q,
            int page,
            int size) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        OpportunityStage parsed = null;
        if (stage != null && !stage.isBlank()) {
            try {
                parsed = OpportunityStage.fromString(stage);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid stage: " + stage);
            }
        }
        UUID effectiveConsultantId = member.getAgencyRole() == AgencyRole.AGENCY_CONSULTANT
                ? null
                : consultantId;
        return opportunityRepository
                .search(member.getAgency().id, parsed, effectiveConsultantId, clientId, q, page, size)
                .stream()
                .filter(opp -> member.getAgencyRole() == AgencyRole.AGENCY_OWNER
                        || isAssignedTo(opp, userId))
                .map(this::toDto)
                .toList();
    }

    public AgencyOpportunityDTO get(UUID userId, UUID opportunityId) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        return toDto(requireAccessibleOpportunity(member, userId, opportunityId));
    }

    @Transactional
    public AgencyOpportunityDTO create(UUID userId, UpsertAgencyOpportunityRequest request) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        if (request == null) {
            throw new BadRequestException("body is required");
        }
        AgencyClient client = resolveOrCreateClient(member.getAgency(), request);
        User consultant = resolveConsultant(member.getAgency().id, request.getAssignedConsultantId(), userId);
        String summary = blankToNull(request.getRequestSummary());
        String title = blankToNull(request.getTitle());
        if (title == null) {
            title = summary != null ? truncate(summary, 80) : ("Solicitação — " + client.getName());
        }
        String leadSource = normalizeLeadSource(request.getLeadSource());

        OpportunityStage stage = OpportunityStage.NEW;
        if (request.getStage() != null && !request.getStage().isBlank()) {
            stage = OpportunityStage.fromString(request.getStage());
        }

        AgencyOpportunity opp = AgencyOpportunity.builder()
                .agency(member.getAgency())
                .client(client)
                .title(title)
                .stage(stage)
                .requestSummary(summary)
                .assignedConsultant(consultant)
                .nextFollowUpAt(request.getNextFollowUpAt())
                .priority(parsePriority(request.getPriority()))
                .lastActivityAt(Instant.now())
                .leadSource(leadSource)
                .leadSourceDetail(blankToNull(request.getLeadSourceDetail()))
                .build();
        applyDetails(opp, request);
        if (opp.getEstimatedValue() == null) {
            opp.setEstimatedValue(opp.getBudgetMax() != null ? opp.getBudgetMax() : opp.getBudgetMin());
        }
        refreshQualification(opp);
        opportunityRepository.persist(opp);
        recordActivity(opp, userRepository.findById(userId), OpportunityActivityType.CREATED,
                "Oportunidade criada", null);
        return toDto(opp);
    }

    @Transactional
    public AgencyOpportunityDTO update(UUID userId, UUID opportunityId, UpsertAgencyOpportunityRequest request) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        AgencyOpportunity opp = requireAccessibleOpportunity(member, userId, opportunityId);
        if (request == null) {
            throw new BadRequestException("body is required");
        }
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            opp.setTitle(request.getTitle().trim());
        }
        if (request.getRequestSummary() != null) {
            opp.setRequestSummary(blankToNull(request.getRequestSummary()));
        }
        if (request.getLeadSource() != null) {
            opp.setLeadSource(normalizeLeadSource(request.getLeadSource()));
        }
        if (request.getLeadSourceDetail() != null) {
            opp.setLeadSourceDetail(blankToNull(request.getLeadSourceDetail()));
        }
        boolean assigneeChanged = false;
        if (request.getAssignedConsultantId() != null) {
            UUID previous = opp.getAssignedConsultant() != null ? opp.getAssignedConsultant().id : null;
            opp.setAssignedConsultant(
                    resolveConsultant(member.getAgency().id, request.getAssignedConsultantId(), userId));
            assigneeChanged = !request.getAssignedConsultantId().equals(previous);
        }
        if (request.getNextFollowUpAt() != null) {
            opp.setNextFollowUpAt(request.getNextFollowUpAt());
        }
        OpportunityStage previousStage = opp.getStage();
        if (request.getStage() != null && !request.getStage().isBlank()) {
            applyStage(opp, OpportunityStage.fromString(request.getStage()), request.getLostReason());
        }
        applyDetails(opp, request);
        User actor = userRepository.findById(userId);
        if (previousStage != opp.getStage()) {
            recordActivity(opp, actor, OpportunityActivityType.STAGE_CHANGED,
                    "Etapa alterada para " + opp.getStage().name(), null);
        }
        if (assigneeChanged) {
            recordActivity(opp, actor, OpportunityActivityType.ASSIGNEE_CHANGED,
                    "Consultor responsável alterado", null);
        }
        if (hasNextActionChange(request)) {
            recordActivity(opp, actor, OpportunityActivityType.NEXT_ACTION_SET,
                    "Próxima ação atualizada", opp.getNextActionNote());
        }
        if (blankToNull(request.getActivityNote()) != null) {
            recordActivity(opp, actor, OpportunityActivityType.NOTE, "Nota", request.getActivityNote());
        }
        opp.setLastActivityAt(Instant.now());
        refreshQualification(opp);
        opportunityRepository.persist(opp);
        return toDto(opp);
    }

    @Transactional
    public AgencyOpportunityDTO markLost(UUID userId, UUID opportunityId, MarkOpportunityLostRequest request) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        AgencyOpportunity opp = requireAccessibleOpportunity(member, userId, opportunityId);
        if (request == null) {
            throw new BadRequestException("body is required");
        }
        OpportunityLostReasonCode code = parseLostReasonCode(request.getLostReasonCode());
        String reason = blankToNull(request.getLostReason());
        if (code == null && reason == null) {
            throw new BadRequestException("lostReasonCode or lostReason is required");
        }
        opp.setLostReasonCode(code);
        opp.setLostReason(reason != null ? reason : code.name());
        opp.setLostCompetitor(blankToNull(request.getLostCompetitor()));
        opp.setLostNote(blankToNull(request.getLostNote()));
        opp.setLostMayReactivate(Boolean.TRUE.equals(request.getLostMayReactivate()));
        opp.setLostReactivateAt(request.getLostReactivateAt());
        applyStage(opp, OpportunityStage.LOST, opp.getLostReason());
        opp.setLastActivityAt(Instant.now());
        opportunityRepository.persist(opp);
        recordActivity(opp, userRepository.findById(userId), OpportunityActivityType.LOST,
                "Oportunidade marcada como perdida", opp.getLostReason());
        return toDto(opp);
    }

    @Transactional
    public AgencyOpportunityDTO markWon(UUID userId, UUID opportunityId) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        AgencyOpportunity opp = requireAccessibleOpportunity(member, userId, opportunityId);
        applyStage(opp, OpportunityStage.WON, null);
        opp.setLastActivityAt(Instant.now());
        opportunityRepository.persist(opp);
        recordActivity(opp, userRepository.findById(userId), OpportunityActivityType.WON,
                "Oportunidade marcada como ganha", null);
        return toDto(opp);
    }

    /**
     * Alinha o estágio da solicitação ao status da proposta (Trip).
     * Confirmada → Ganho; cancelada/rejeitada/perdida → Perdido.
     */
    @Transactional
    public void syncStageFromProposalStatus(UUID tripId, ProposalStatus proposalStatus) {
        syncStageFromProposalStatus(tripId, proposalStatus, null);
    }

    @Transactional
    public void syncStageFromProposalStatus(
            UUID tripId, ProposalStatus proposalStatus, String lostReasonOverride) {
        if (tripId == null || proposalStatus == null) {
            return;
        }
        opportunityRepository.findByTripId(tripId).ifPresent(opp -> {
            OpportunityStage current = opp.getStage() != null ? opp.getStage() : OpportunityStage.NEW;
            switch (proposalStatus) {
                case CONFIRMED, IN_TRIP, COMPLETED, APPROVED -> {
                    if (current == OpportunityStage.WON) {
                        return;
                    }
                    applyStage(opp, OpportunityStage.WON, null);
                    opp.setLastActivityAt(Instant.now());
                    opportunityRepository.persist(opp);
                    recordActivity(opp, null, OpportunityActivityType.WON,
                            "Marcada como ganha (proposta confirmada)", null);
                }
                case REJECTED, LOST, CANCELLED -> {
                    if (current == OpportunityStage.LOST) {
                        return;
                    }
                    OpportunityLostReasonCode code = proposalStatus == ProposalStatus.CANCELLED
                            ? OpportunityLostReasonCode.CLIENT_CANCELLED
                            : OpportunityLostReasonCode.OTHER;
                    String reason = blankToNull(lostReasonOverride);
                    if (reason == null && opp.getTrip() != null) {
                        reason = blankToNull(opp.getTrip().getProposalRejectReason());
                    }
                    if (reason == null) {
                        reason = proposalStatus == ProposalStatus.REJECTED
                                ? "Proposta rejeitada pelo cliente"
                                : proposalStatus == ProposalStatus.CANCELLED
                                    ? "Proposta/viagem cancelada"
                                    : "Proposta marcada como perdida";
                    }
                    opp.setLostReasonCode(code);
                    opp.setLostReason(reason);
                    applyStage(opp, OpportunityStage.LOST, reason);
                    opp.setLastActivityAt(Instant.now());
                    opportunityRepository.persist(opp);
                    recordActivity(opp, null, OpportunityActivityType.LOST,
                            "Marcada como perdida (proposta " + proposalStatus.name().toLowerCase() + ")",
                            reason);
                }
                default -> {
                    // PENDING_PAYMENT e demais: não fecha a solicitação ainda
                }
            }
        });
    }

    /**
     * Converte a solicitação em proposta (Trip) sem redigitar dados.
     */
    @Transactional
    public AgencyOpportunityDTO convertToProposal(UUID userId, UUID opportunityId) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        AgencyOpportunity opp = requireAccessibleOpportunity(member, userId, opportunityId);
        // Serializa conversões concorrentes (ex.: loop no front) para 1 Trip só.
        opportunityRepository.getEntityManager().lock(opp, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        opportunityRepository.getEntityManager().refresh(opp);
        if (opp.getTrip() != null) {
            return toDto(opp);
        }
        User creator = userRepository.findById(userId);
        if (creator == null) {
            throw new NotFoundException("User not found");
        }
        Workspace workspace = resolveWorkspace(creator);
        AgencyClient client = opp.getClient();

        LocalDate start = opp.getStartDate() != null
                ? opp.getStartDate()
                : LocalDate.now().plusMonths(2);
        int days = opp.getDurationDays() != null && opp.getDurationDays() > 0
                ? opp.getDurationDays()
                : 7;
        LocalDate end = opp.getEndDate() != null ? opp.getEndDate() : start.plusDays(Math.max(days - 1, 0));

        String dest = blankToNull(opp.getDestinations());
        String description = buildTripDescription(opp);

        Trip trip = Trip.builder()
                .name(opp.getTitle())
                .description(description)
                .workspace(workspace)
                .createdBy(creator)
                .agency(member.getAgency())
                .client(client)
                .assignedConsultant(opp.getAssignedConsultant() != null
                        ? opp.getAssignedConsultant()
                        : creator)
                .status(TripStatus.PLANNING)
                .proposalStatus(ProposalStatus.QUOTING)
                .shareCode(ProposalService.generateShareCode())
                .startDate(start)
                .endDate(end)
                .durationDays(days)
                .budgetTotal(opp.getBudgetMax() != null
                        ? opp.getBudgetMax()
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
        // Mesmo contrato do CreateTrip: criador precisa estar em trip_users,
        // senão update-trip falha em "Trip must have at least one user".
        tripRepository.addTripMember(trip, creator, "OWNER");

        opp.setTrip(trip);
        if (opp.getStage() == OpportunityStage.NEW
                || opp.getStage() == OpportunityStage.QUALIFYING) {
            opp.setStage(OpportunityStage.QUOTING);
        }
        refreshQualification(opp);
        opp.setLastActivityAt(Instant.now());
        opportunityRepository.persist(opp);
        recordActivity(opp, creator, OpportunityActivityType.PROPOSAL_SENT,
                "Proposta iniciada", null);
        return toDto(opp);
    }

    private void applyStage(AgencyOpportunity opp, OpportunityStage stage, String lostReason) {
        if (stage == OpportunityStage.LOST) {
            if (lostReason == null || lostReason.isBlank()) {
                throw new BadRequestException("lostReason is required to mark as LOST");
            }
            opp.setStage(OpportunityStage.LOST);
            opp.setLostReason(lostReason.trim());
            opp.setLostAt(Instant.now());
            return;
        }
        if (stage == OpportunityStage.WON) {
            opp.setStage(OpportunityStage.WON);
            opp.setWonAt(Instant.now());
            opp.setLostReason(null);
            opp.setLostAt(null);
            AgencyClient client = opp.getClient();
            if (client.getContactStatus() == null
                    || client.getContactStatus() == ContactStatus.PROSPECT) {
                client.setContactStatus(ContactStatus.CLIENT);
                clientRepository.persist(client);
            }
            return;
        }
        opp.setStage(stage);
        if (!stage.isTerminal()) {
            opp.setLostReason(null);
            opp.setLostAt(null);
            opp.setWonAt(null);
        }
    }

    private void applyDetails(AgencyOpportunity opp, UpsertAgencyOpportunityRequest request) {
        if (request.getPriority() != null) {
            opp.setPriority(parsePriority(request.getPriority()));
        }
        if (request.getEstimatedValue() != null) {
            opp.setEstimatedValue(request.getEstimatedValue());
        }
        if (request.getNextActionType() != null) {
            opp.setNextActionType(parseNextActionType(request.getNextActionType()));
        }
        if (request.getNextActionAt() != null) {
            opp.setNextActionAt(request.getNextActionAt());
        }
        if (request.getNextActionNote() != null) {
            opp.setNextActionNote(blankToNull(request.getNextActionNote()));
        }
        if (request.getNextActionAssigneeId() != null) {
            opp.setNextActionAssignee(resolveConsultant(
                    opp.getAgency().id, request.getNextActionAssigneeId(),
                    opp.getAssignedConsultant() != null
                            ? opp.getAssignedConsultant().id
                            : request.getNextActionAssigneeId()));
        }
        if (request.getPreferredChannel() != null) {
            opp.setPreferredChannel(blankToNull(request.getPreferredChannel()));
        }
        if (request.getBestContactTime() != null) {
            opp.setBestContactTime(blankToNull(request.getBestContactTime()));
        }
        if (request.getCity() != null) {
            opp.setCity(blankToNull(request.getCity()));
        }
        if (request.getCountry() != null) {
            opp.setCountry(blankToNull(request.getCountry()));
        }
        if (request.getPassenger() != null) {
            opp.setPassenger(request.getPassenger());
        }
        if (request.getDecisionMakers() != null) {
            opp.setDecisionMakers(blankToNull(request.getDecisionMakers()));
        }
        if (request.getOriginCity() != null) {
            opp.setOriginCity(blankToNull(request.getOriginCity()));
        }
        if (request.getDestinations() != null) {
            opp.setDestinations(blankToNull(request.getDestinations()));
        }
        if (request.getStartDate() != null) {
            opp.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            opp.setEndDate(request.getEndDate());
        }
        if (request.getDurationDays() != null) {
            opp.setDurationDays(request.getDurationDays());
        }
        if (request.getDatesFlexible() != null) {
            opp.setDatesFlexible(request.getDatesFlexible());
        }
        if (request.getAlternateAirports() != null) {
            opp.setAlternateAirports(blankToNull(request.getAlternateAirports()));
        }
        if (request.getTripType() != null) {
            opp.setTripType(blankToNull(request.getTripType()));
        }
        if (request.getAdults() != null) {
            opp.setAdults(request.getAdults());
        }
        if (request.getChildrenCount() != null) {
            opp.setChildrenCount(request.getChildrenCount());
        }
        if (request.getChildrenAges() != null) {
            opp.setChildrenAges(blankToNull(request.getChildrenAges()));
        }
        if (request.getInfants() != null) {
            opp.setInfants(request.getInfants());
        }
        if (request.getRooms() != null) {
            opp.setRooms(request.getRooms());
        }
        if (request.getOccupancyPreference() != null) {
            opp.setOccupancyPreference(blankToNull(request.getOccupancyPreference()));
        }
        if (request.getPassengersEstimated() != null) {
            opp.setPassengersEstimated(request.getPassengersEstimated());
        }
        if (request.getDesiredServices() != null) {
            opp.setDesiredServices(joinList(request.getDesiredServices()));
        }
        if (request.getBudgetMin() != null) {
            opp.setBudgetMin(request.getBudgetMin());
        }
        if (request.getBudgetMax() != null) {
            opp.setBudgetMax(request.getBudgetMax());
        }
        if (request.getBudgetCurrency() != null) {
            opp.setBudgetCurrency(blankToNull(request.getBudgetCurrency()));
        }
        if (request.getBudgetPerPerson() != null) {
            opp.setBudgetPerPerson(request.getBudgetPerPerson());
        }
        if (request.getBudgetIncludesFlights() != null) {
            opp.setBudgetIncludesFlights(request.getBudgetIncludesFlights());
        }
        if (request.getPaymentPreference() != null) {
            opp.setPaymentPreference(blankToNull(request.getPaymentPreference()));
        }
        if (request.getAcceptsInstallments() != null) {
            opp.setAcceptsInstallments(request.getAcceptsInstallments());
        }
        if (request.getBudgetEstimatedByAgent() != null) {
            opp.setBudgetEstimatedByAgent(request.getBudgetEstimatedByAgent());
        }
        if (request.getPreferences() != null) {
            opp.setPreferences(joinList(request.getPreferences()));
        }
        if (request.getRestrictions() != null) {
            opp.setRestrictions(blankToNull(request.getRestrictions()));
        }
        if (request.getDecisionDeadline() != null) {
            opp.setDecisionDeadline(request.getDecisionDeadline());
        }
        if (request.getUrgency() != null) {
            opp.setUrgency(blankToNull(request.getUrgency()));
        }
        if (request.getHasOtherProposals() != null) {
            opp.setHasOtherProposals(request.getHasOtherProposals());
        }
        if (request.getHasExistingReservation() != null) {
            opp.setHasExistingReservation(request.getHasExistingReservation());
        }
        if (request.getDecisionMaker() != null) {
            opp.setDecisionMaker(blankToNull(request.getDecisionMaker()));
        }
        if (request.getMainCriterion() != null) {
            opp.setMainCriterion(blankToNull(request.getMainCriterion()));
        }
        if (request.getReadyToQuoteOverride() != null) {
            opp.setReadyToQuoteOverride(request.getReadyToQuoteOverride());
        }
        if (request.getQualificationStatus() != null && !request.getQualificationStatus().isBlank()) {
            opp.setQualificationStatus(QualificationStatus.fromString(request.getQualificationStatus()));
        }
    }

    private void refreshQualification(AgencyOpportunity opp) {
        AgencyOpportunityDTO.QualificationChecklistDTO checklist = buildChecklist(opp);
        if (opp.isReadyToQuoteOverride()
                || "READY_TO_QUOTE".equals(checklist.getSuggestedStatus())) {
            opp.setQualificationStatus(QualificationStatus.READY_TO_QUOTE);
        } else if ("PARTIAL".equals(checklist.getSuggestedStatus())) {
            opp.setQualificationStatus(QualificationStatus.PARTIAL);
        } else {
            opp.setQualificationStatus(QualificationStatus.INSUFFICIENT);
        }
    }

    private AgencyOpportunityDTO.QualificationChecklistDTO buildChecklist(AgencyOpportunity opp) {
        AgencyClient client = opp.getClient();
        boolean validContact = client != null
                && ((client.getEmail() != null && !client.getEmail().isBlank())
                || (client.getPhone() != null && !client.getPhone().isBlank()));
        boolean hasDestination = opp.getDestinations() != null && !opp.getDestinations().isBlank();
        boolean hasPassengers = opp.getAdults() != null && opp.getAdults() > 0;
        boolean hasDates = opp.isDatesFlexible()
                || opp.getStartDate() != null
                || (opp.getDurationDays() != null && opp.getDurationDays() > 0);
        boolean hasServices = opp.getDesiredServices() != null && !opp.getDesiredServices().isBlank();
        boolean hasBudget = opp.getBudgetMin() != null || opp.getBudgetMax() != null;
        boolean hasDecisionDeadline = opp.getDecisionDeadline() != null
                || (opp.getUrgency() != null && !opp.getUrgency().isBlank());

        int done = 0;
        if (validContact) done++;
        if (hasDestination) done++;
        if (hasPassengers) done++;
        if (hasDates) done++;
        if (hasServices) done++;
        if (hasBudget) done++;
        if (hasDecisionDeadline) done++;
        int total = 7;

        String suggested;
        if (done >= 5 && validContact && hasDestination) {
            suggested = "READY_TO_QUOTE";
        } else if (done >= 3) {
            suggested = "PARTIAL";
        } else {
            suggested = "INSUFFICIENT";
        }

        return AgencyOpportunityDTO.QualificationChecklistDTO.builder()
                .done(done)
                .total(total)
                .validContact(validContact)
                .hasDestination(hasDestination)
                .hasPassengers(hasPassengers)
                .hasDates(hasDates)
                .hasServices(hasServices)
                .hasBudget(hasBudget)
                .hasDecisionDeadline(hasDecisionDeadline)
                .suggestedStatus(suggested)
                .build();
    }

    private AgencyClient resolveOrCreateClient(Agency agency, UpsertAgencyOpportunityRequest request) {
        if (request.getClientId() != null) {
            AgencyClient existing = clientRepository.findById(request.getClientId());
            if (existing == null || !existing.getAgency().id.equals(agency.id)) {
                throw new NotFoundException("Client not found");
            }
            return existing;
        }
        String name = blankToNull(request.getClientName());
        if (name == null) {
            throw new BadRequestException("clientId or clientName is required");
        }
        String email = normalizeEmail(request.getClientEmail());
        String phone = blankToNull(request.getClientPhone());
        if (email == null && phone == null) {
            throw new BadRequestException("WhatsApp/e-mail is required for a new contact");
        }
        if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
            throw new BadRequestException("email is invalid");
        }
        if (email != null) {
            var byEmail = clientRepository.findByAgencyAndEmail(agency.id, email);
            if (byEmail.isPresent()) {
                return byEmail.get();
            }
        }
        if (phone != null) {
            AgencyClient byPhone = clientRepository
                    .find("agency.id = ?1 AND phone = ?2", agency.id, phone)
                    .firstResult();
            if (byPhone != null) {
                return byPhone;
            }
        }
        AgencyClient client = AgencyClient.builder()
                .agency(agency)
                .name(name)
                .email(email)
                .phone(phone)
                .contactStatus(ContactStatus.PROSPECT)
                .user(email != null ? userRepository.findByEmail(email).orElse(null) : null)
                .build();
        clientRepository.persist(client);
        return client;
    }

    private User resolveConsultant(UUID agencyId, UUID consultantId, UUID fallbackUserId) {
        UUID target = consultantId != null ? consultantId : fallbackUserId;
        AgencyMember member = agencyService.requireMembershipOrThrow(target);
        if (!member.getAgency().id.equals(agencyId)) {
            throw new BadRequestException("Consultant is not a member of this agency");
        }
        User user = userRepository.findById(target);
        if (user == null) {
            throw new BadRequestException("Consultant not found");
        }
        return user;
    }

    private AgencyOpportunity requireOpportunity(UUID agencyId, UUID opportunityId) {
        AgencyOpportunity opp = opportunityRepository.findById(opportunityId);
        if (opp == null || opp.getAgency() == null || !opp.getAgency().id.equals(agencyId)) {
            throw new NotFoundException("Opportunity not found");
        }
        return opp;
    }

    private AgencyOpportunity requireAccessibleOpportunity(
            AgencyMember member, UUID userId, UUID opportunityId) {
        AgencyOpportunity opp = requireOpportunity(member.getAgency().id, opportunityId);
        if (member.getAgencyRole() == AgencyRole.AGENCY_CONSULTANT && !isAssignedTo(opp, userId)) {
            throw new ForbiddenException("You do not have access to this opportunity");
        }
        return opp;
    }

    private static boolean isAssignedTo(AgencyOpportunity opp, UUID userId) {
        return (opp.getAssignedConsultant() != null && userId.equals(opp.getAssignedConsultant().id))
                || (opp.getNextActionAssignee() != null && userId.equals(opp.getNextActionAssignee().id));
    }

    public List<AgencyOpportunityActivityDTO> listActivities(UUID userId, UUID opportunityId, int limit) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        requireAccessibleOpportunity(member, userId, opportunityId);
        return activityRepository.listByOpportunity(opportunityId, limit).stream()
                .map(this::toActivityDto)
                .toList();
    }

    @Transactional
    public AgencyOpportunityActivityDTO addActivity(
            UUID userId, UUID opportunityId, AddOpportunityActivityRequest request) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        AgencyOpportunity opp = requireAccessibleOpportunity(member, userId, opportunityId);
        if (request == null || request.getActivityType() == null || request.getActivityType().isBlank()) {
            throw new BadRequestException("activityType is required");
        }
        OpportunityActivityType type;
        try {
            type = OpportunityActivityType.valueOf(request.getActivityType().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid activityType: " + request.getActivityType());
        }
        if (!List.of(OpportunityActivityType.NOTE, OpportunityActivityType.CALL,
                OpportunityActivityType.MESSAGE, OpportunityActivityType.TASK,
                OpportunityActivityType.OTHER).contains(type)) {
            throw new BadRequestException("Unsupported activityType");
        }
        String title = blankToNull(request.getTitle());
        AgencyOpportunityActivity activity = recordActivity(opp, userRepository.findById(userId), type,
                title != null ? title : type.name(), blankToNull(request.getBody()));
        // Primeiro contato registrado → Qualificando
        if (opp.getStage() == OpportunityStage.NEW
                && List.of(OpportunityActivityType.NOTE, OpportunityActivityType.CALL,
                OpportunityActivityType.MESSAGE).contains(type)) {
            applyStage(opp, OpportunityStage.QUALIFYING, null);
            recordActivity(opp, userRepository.findById(userId), OpportunityActivityType.STAGE_CHANGED,
                    "Etapa alterada para QUALIFYING", null);
        }
        opp.setLastActivityAt(Instant.now());
        opportunityRepository.persist(opp);
        return toActivityDto(activity);
    }

    public DuplicateContactCheckResponse checkDuplicateContacts(UUID userId, String email, String phone) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        String normalizedEmail = normalizeEmail(email);
        String normalizedPhone = normalizePhone(phone);
        if (normalizedEmail == null && normalizedPhone == null) {
            return DuplicateContactCheckResponse.builder().hasMatches(false).matches(List.of()).build();
        }
        List<DuplicateContactCheckResponse.Match> matches = clientRepository.findByAgencyId(member.getAgency().id)
                .stream()
                .filter(client -> normalizedEmail != null && normalizedEmail.equals(normalizeEmail(client.getEmail()))
                        || normalizedPhone != null && normalizedPhone.equals(normalizePhone(client.getPhone())))
                .map(client -> DuplicateContactCheckResponse.Match.builder()
                        .clientId(client.id)
                        .name(client.getName())
                        .email(client.getEmail())
                        .phone(client.getPhone())
                        .contactStatus(client.getContactStatus() != null ? client.getContactStatus().name() : null)
                        .opportunityCount(opportunityRepository.listByClient(member.getAgency().id, client.id).size())
                        .build())
                .toList();
        return DuplicateContactCheckResponse.builder()
                .hasMatches(!matches.isEmpty())
                .matches(matches)
                .build();
    }

    private AgencyOpportunityActivity recordActivity(
            AgencyOpportunity opp, User actor, OpportunityActivityType type, String title, String body) {
        AgencyOpportunityActivity activity = AgencyOpportunityActivity.builder()
                .opportunity(opp)
                .agency(opp.getAgency())
                .actor(actor)
                .actorLabel(actor != null
                        ? (actor.getFullName() != null ? actor.getFullName() : actor.getEmail())
                        : null)
                .activityType(type)
                .title(title)
                .body(body)
                .build();
        activityRepository.persist(activity);
        return activity;
    }

    /**
     * Atividade na oportunidade ligada a uma Trip (envio/visualização/aprovação da proposta).
     */
    @Transactional
    public void recordActivityForTrip(
            UUID tripId, OpportunityActivityType type, String title, String body) {
        if (tripId == null || type == null) {
            return;
        }
        opportunityRepository.findByTripId(tripId).ifPresent(opp -> {
            recordActivity(opp, null, type, title, body);
            if (type == OpportunityActivityType.PROPOSAL_SENT
                    && (opp.getStage() == OpportunityStage.NEW
                    || opp.getStage() == OpportunityStage.QUALIFYING
                    || opp.getStage() == OpportunityStage.QUOTING)) {
                applyStage(opp, OpportunityStage.NEGOTIATING, null);
            }
            opp.setLastActivityAt(Instant.now());
            opportunityRepository.persist(opp);
        });
    }

    // ── Tasks ───────────────────────────────────────────────────────────────

    public List<AgencyOpportunityTaskDTO> listTasks(UUID userId, UUID opportunityId) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        requireAccessibleOpportunity(member, userId, opportunityId);
        return taskRepository.listByOpportunity(opportunityId).stream()
                .map(this::toTaskDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public AgencyOpportunityTaskDTO createTask(
            UUID userId, UUID opportunityId, UpsertOpportunityTaskRequest request) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        AgencyOpportunity opp = requireAccessibleOpportunity(member, userId, opportunityId);
        if (request == null || request.getTitle() == null || request.getTitle().isBlank()) {
            throw new BadRequestException("title is required");
        }
        User assignee = null;
        if (request.getAssigneeUserId() != null) {
            assignee = userRepository.findById(request.getAssigneeUserId());
            if (assignee == null) {
                throw new NotFoundException("Assignee not found");
            }
        }
        AgencyOpportunityTask task = AgencyOpportunityTask.builder()
                .opportunity(opp)
                .agency(opp.getAgency())
                .title(request.getTitle().trim())
                .status(OpportunityTaskStatus.OPEN)
                .dueAt(request.getDueAt())
                .assignee(assignee)
                .build();
        taskRepository.persist(task);
        recordActivity(opp, userRepository.findById(userId), OpportunityActivityType.TASK,
                "Tarefa criada: " + task.getTitle(), null);
        opp.setLastActivityAt(Instant.now());
        if (opp.getNextActionAt() == null && request.getDueAt() != null) {
            opp.setNextActionAt(request.getDueAt());
            opp.setNextActionNote(task.getTitle());
            if (assignee != null) {
                opp.setNextActionAssignee(assignee);
            }
        }
        opportunityRepository.persist(opp);
        return toTaskDto(task);
    }

    @Transactional
    public AgencyOpportunityTaskDTO updateTask(
            UUID userId, UUID opportunityId, UUID taskId, UpsertOpportunityTaskRequest request) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        requireAccessibleOpportunity(member, userId, opportunityId);
        AgencyOpportunityTask task = taskRepository.findById(taskId);
        if (task == null || task.getOpportunity() == null || !opportunityId.equals(task.getOpportunity().id)) {
            throw new NotFoundException("Task not found");
        }
        if (request == null) {
            throw new BadRequestException("body is required");
        }
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            task.setTitle(request.getTitle().trim());
        }
        if (request.getDueAt() != null) {
            task.setDueAt(request.getDueAt());
        }
        if (request.getAssigneeUserId() != null) {
            User assignee = userRepository.findById(request.getAssigneeUserId());
            if (assignee == null) {
                throw new NotFoundException("Assignee not found");
            }
            task.setAssignee(assignee);
        }
        if (request.getStatus() != null) {
            OpportunityTaskStatus status = OpportunityTaskStatus.fromString(request.getStatus());
            task.setStatus(status);
            if (status == OpportunityTaskStatus.DONE) {
                task.setCompletedAt(Instant.now());
            } else {
                task.setCompletedAt(null);
            }
        }
        taskRepository.persist(task);
        return toTaskDto(task);
    }

    @Transactional
    public void deleteTask(UUID userId, UUID opportunityId, UUID taskId) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        requireAccessibleOpportunity(member, userId, opportunityId);
        AgencyOpportunityTask task = taskRepository.findById(taskId);
        if (task == null || task.getOpportunity() == null || !opportunityId.equals(task.getOpportunity().id)) {
            throw new NotFoundException("Task not found");
        }
        taskRepository.delete(task);
    }

    // ── Files ───────────────────────────────────────────────────────────────

    public List<AgencyOpportunityFileDTO> listFiles(UUID userId, UUID opportunityId) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        requireAccessibleOpportunity(member, userId, opportunityId);
        return fileRepository.listByOpportunity(opportunityId).stream()
                .map(f -> toFileDto(f, false))
                .collect(Collectors.toList());
    }

    @Transactional
    public AgencyOpportunityFileDTO uploadFile(
            UUID userId,
            UUID opportunityId,
            String fileName,
            String contentType,
            byte[] bytes,
            String kindHint) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        AgencyOpportunity opp = requireAccessibleOpportunity(member, userId, opportunityId);
        if (!objectStorageService.isConfigured()) {
            throw new BadRequestException("Document storage is not configured");
        }
        var resolved = DocumentUploadSupport.resolve(fileName, contentType);
        if (resolved.isEmpty()) {
            throw new BadRequestException(
                    DocumentUploadSupport.unsupportedTypeMessage(contentType, fileName));
        }
        DocumentUploadSupport.ResolvedUpload upload = resolved.get();
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("File is empty");
        }
        if (bytes.length > DocumentUploadSupport.MAX_UPLOAD_BYTES) {
            throw new BadRequestException("File exceeds 10 MB limit");
        }
        String extension = DocumentUploadSupport.extractExtension(upload.fileName());
        String storageKey = "opportunities/" + opportunityId + "/files/"
                + UUID.randomUUID() + extension;
        objectStorageService.putObject(storageKey, bytes, upload.contentType());
        OpportunityFileKind kind = kindHint != null && !kindHint.isBlank()
                ? OpportunityFileKind.fromString(kindHint)
                : OpportunityFileKind.fromContentType(upload.contentType(), upload.fileName());
        User uploader = userRepository.findById(userId);
        AgencyOpportunityFile file = AgencyOpportunityFile.builder()
                .opportunity(opp)
                .agency(opp.getAgency())
                .fileName(upload.fileName())
                .contentType(upload.contentType())
                .sizeBytes((long) bytes.length)
                .storageKey(storageKey)
                .kind(kind)
                .uploadedBy(uploader)
                .build();
        fileRepository.persist(file);
        recordActivity(opp, uploader, OpportunityActivityType.OTHER,
                "Arquivo anexado: " + file.getFileName(), kind.name());
        opp.setLastActivityAt(Instant.now());
        opportunityRepository.persist(opp);
        return toFileDto(file, true);
    }

    public AgencyOpportunityFileDTO getFileView(UUID userId, UUID opportunityId, UUID fileId) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        requireAccessibleOpportunity(member, userId, opportunityId);
        AgencyOpportunityFile file = fileRepository.findById(fileId);
        if (file == null || file.getOpportunity() == null || !opportunityId.equals(file.getOpportunity().id)) {
            throw new NotFoundException("File not found");
        }
        return toFileDto(file, true);
    }

    @Transactional
    public void deleteFile(UUID userId, UUID opportunityId, UUID fileId) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        requireAccessibleOpportunity(member, userId, opportunityId);
        AgencyOpportunityFile file = fileRepository.findById(fileId);
        if (file == null || file.getOpportunity() == null || !opportunityId.equals(file.getOpportunity().id)) {
            throw new NotFoundException("File not found");
        }
        try {
            objectStorageService.deleteObject(file.getStorageKey());
        } catch (Exception ignored) {
            // best-effort
        }
        fileRepository.delete(file);
    }

    private AgencyOpportunityTaskDTO toTaskDto(AgencyOpportunityTask task) {
        User assignee = task.getAssignee();
        boolean overdue = task.getStatus() == OpportunityTaskStatus.OPEN
                && task.getDueAt() != null
                && task.getDueAt().isBefore(Instant.now());
        return AgencyOpportunityTaskDTO.builder()
                .id(task.id)
                .opportunityId(task.getOpportunity() != null ? task.getOpportunity().id : null)
                .title(task.getTitle())
                .status(task.getStatus() != null ? task.getStatus().name() : OpportunityTaskStatus.OPEN.name())
                .dueAt(task.getDueAt())
                .assigneeUserId(assignee != null ? assignee.id : null)
                .assigneeName(assignee != null
                        ? (assignee.getFullName() != null ? assignee.getFullName() : assignee.getEmail())
                        : null)
                .completedAt(task.getCompletedAt())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .overdue(overdue)
                .build();
    }

    private AgencyOpportunityFileDTO toFileDto(AgencyOpportunityFile file, boolean withViewUrl) {
        User uploader = file.getUploadedBy();
        String viewUrl = null;
        if (withViewUrl && objectStorageService.isConfigured()) {
            try {
                viewUrl = objectStorageService.presignGet(file.getStorageKey());
            } catch (Exception ignored) {
                viewUrl = null;
            }
        }
        return AgencyOpportunityFileDTO.builder()
                .id(file.id)
                .opportunityId(file.getOpportunity() != null ? file.getOpportunity().id : null)
                .fileName(file.getFileName())
                .contentType(file.getContentType())
                .sizeBytes(file.getSizeBytes())
                .kind(file.getKind() != null ? file.getKind().name() : OpportunityFileKind.OTHER.name())
                .uploadedByUserId(uploader != null ? uploader.id : null)
                .uploadedByName(uploader != null
                        ? (uploader.getFullName() != null ? uploader.getFullName() : uploader.getEmail())
                        : null)
                .createdAt(file.getCreatedAt())
                .viewUrl(viewUrl)
                .build();
    }

    private AgencyOpportunityActivityDTO toActivityDto(AgencyOpportunityActivity activity) {
        User actor = activity.getActor();
        return AgencyOpportunityActivityDTO.builder()
                .id(activity.id)
                .opportunityId(activity.getOpportunity() != null ? activity.getOpportunity().id : null)
                .activityType(activity.getActivityType().name())
                .title(activity.getTitle())
                .body(activity.getBody())
                .actorUserId(actor != null ? actor.id : null)
                .actorLabel(activity.getActorLabel())
                .createdAt(activity.getCreatedAt())
                .build();
    }

    private static boolean hasNextActionChange(UpsertAgencyOpportunityRequest request) {
        return request.getNextActionType() != null || request.getNextActionAt() != null
                || request.getNextActionNote() != null || request.getNextActionAssigneeId() != null;
    }

    private static OpportunityPriority parsePriority(String value) {
        try {
            return OpportunityPriority.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid priority: " + value);
        }
    }

    private static OpportunityNextActionType parseNextActionType(String value) {
        try {
            return OpportunityNextActionType.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid nextActionType: " + value);
        }
    }

    private static OpportunityLostReasonCode parseLostReasonCode(String value) {
        try {
            return OpportunityLostReasonCode.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid lostReasonCode: " + value);
        }
    }

    private AgencyOpportunityDTO toDto(AgencyOpportunity opp) {
        AgencyClient client = opp.getClient();
        User consultant = opp.getAssignedConsultant();
        User nextActionAssignee = opp.getNextActionAssignee();
        Trip trip = opp.getTrip();
        Instant now = Instant.now();
        boolean terminal = opp.getStage() == OpportunityStage.WON || opp.getStage() == OpportunityStage.LOST;
        boolean missingNextAction = !terminal && opp.getStage() != OpportunityStage.NEW
                && opp.getNextActionAt() == null;
        boolean overdue = !terminal && opp.getNextActionAt() != null
                && opp.getNextActionAt().isBefore(now);
        String health = overdue
                ? "OVERDUE"
                : !terminal && opp.getLastActivityAt() != null
                        && opp.getLastActivityAt().isBefore(now.minusSeconds(3 * 24 * 60 * 60))
                ? "STALE"
                : trip != null && trip.getProposalLastViewedAt() != null
                        && trip.getProposalLastViewedAt().isAfter(now.minusSeconds(48 * 60 * 60))
                ? "VIEWED"
                : "OK";
        return AgencyOpportunityDTO.builder()
                .id(opp.id)
                .agencyId(opp.getAgency() != null ? opp.getAgency().id : null)
                .clientId(client != null ? client.id : null)
                .clientName(client != null ? client.getName() : null)
                .clientEmail(client != null ? client.getEmail() : null)
                .clientPhone(client != null ? client.getPhone() : null)
                .clientContactStatus(client != null && client.getContactStatus() != null
                        ? client.getContactStatus().name()
                        : ContactStatus.PROSPECT.name())
                .tripId(trip != null ? trip.id : null)
                .tripShareCode(trip != null ? trip.getShareCode() : null)
                .title(opp.getTitle())
                .stage(opp.getStage() != null ? opp.getStage().name() : OpportunityStage.NEW.name())
                .requestSummary(opp.getRequestSummary())
                .assignedConsultantId(consultant != null ? consultant.id : null)
                .assignedConsultantName(consultant != null
                        ? (consultant.getFullName() != null ? consultant.getFullName() : consultant.getEmail())
                        : null)
                .nextFollowUpAt(opp.getNextFollowUpAt())
                .leadSource(opp.getLeadSource())
                .leadSourceDetail(opp.getLeadSourceDetail())
                .preferredChannel(opp.getPreferredChannel())
                .bestContactTime(opp.getBestContactTime())
                .city(opp.getCity())
                .country(opp.getCountry())
                .passenger(opp.getPassenger())
                .decisionMakers(opp.getDecisionMakers())
                .originCity(opp.getOriginCity())
                .destinations(opp.getDestinations())
                .startDate(opp.getStartDate())
                .endDate(opp.getEndDate())
                .durationDays(opp.getDurationDays())
                .datesFlexible(opp.isDatesFlexible())
                .alternateAirports(opp.getAlternateAirports())
                .tripType(opp.getTripType())
                .adults(opp.getAdults())
                .childrenCount(opp.getChildrenCount())
                .childrenAges(opp.getChildrenAges())
                .infants(opp.getInfants())
                .rooms(opp.getRooms())
                .occupancyPreference(opp.getOccupancyPreference())
                .passengersEstimated(opp.isPassengersEstimated())
                .desiredServices(splitList(opp.getDesiredServices()))
                .budgetMin(opp.getBudgetMin())
                .budgetMax(opp.getBudgetMax())
                .budgetCurrency(opp.getBudgetCurrency())
                .budgetPerPerson(opp.getBudgetPerPerson())
                .budgetIncludesFlights(opp.getBudgetIncludesFlights())
                .paymentPreference(opp.getPaymentPreference())
                .acceptsInstallments(opp.getAcceptsInstallments())
                .budgetEstimatedByAgent(opp.getBudgetEstimatedByAgent())
                .preferences(splitList(opp.getPreferences()))
                .restrictions(opp.getRestrictions())
                .decisionDeadline(opp.getDecisionDeadline())
                .urgency(opp.getUrgency())
                .hasOtherProposals(opp.getHasOtherProposals())
                .hasExistingReservation(opp.getHasExistingReservation())
                .decisionMaker(opp.getDecisionMaker())
                .mainCriterion(opp.getMainCriterion())
                .qualificationStatus(opp.getQualificationStatus() != null
                        ? opp.getQualificationStatus().name()
                        : QualificationStatus.INSUFFICIENT.name())
                .readyToQuoteOverride(opp.isReadyToQuoteOverride())
                .qualification(buildChecklist(opp))
                .priority(opp.getPriority() != null ? opp.getPriority().name() : OpportunityPriority.MEDIUM.name())
                .estimatedValue(opp.getEstimatedValue())
                .lastActivityAt(opp.getLastActivityAt())
                .nextActionType(opp.getNextActionType() != null ? opp.getNextActionType().name() : null)
                .nextActionAt(opp.getNextActionAt())
                .nextActionNote(opp.getNextActionNote())
                .nextActionAssigneeId(nextActionAssignee != null ? nextActionAssignee.id : null)
                .nextActionAssigneeName(nextActionAssignee != null
                        ? (nextActionAssignee.getFullName() != null
                                ? nextActionAssignee.getFullName()
                                : nextActionAssignee.getEmail())
                        : null)
                .missingNextAction(missingNextAction)
                .nextActionOverdue(overdue)
                .proposalCount(trip != null ? 1 : 0)
                .proposalLastViewedAt(trip != null ? trip.getProposalLastViewedAt() : null)
                .proposalViewCount(trip != null && trip.getProposalViewCount() != null
                        ? trip.getProposalViewCount().longValue()
                        : 0L)
                .proposalStatus(trip != null && trip.getProposalStatus() != null
                        ? trip.getProposalStatus().name()
                        : null)
                .proposalFinalPrice(trip != null ? trip.getFinalPrice() : null)
                .proposalBaseCost(trip != null ? trip.getBaseCost() : null)
                .proposalSentAt(trip != null ? trip.getProposalSentAt() : null)
                .proposalExpiresAt(trip != null ? trip.getProposalExpiresAt() : null)
                .health(health)
                .lostReason(opp.getLostReason())
                .lostReasonCode(opp.getLostReasonCode() != null ? opp.getLostReasonCode().name() : null)
                .lostCompetitor(opp.getLostCompetitor())
                .lostNote(opp.getLostNote())
                .lostMayReactivate(opp.isLostMayReactivate())
                .lostReactivateAt(opp.getLostReactivateAt())
                .lostAt(opp.getLostAt())
                .wonAt(opp.getWonAt())
                .createdAt(opp.getCreatedAt())
                .updatedAt(opp.getUpdatedAt())
                .build();
    }

    private static String buildTripDescription(AgencyOpportunity opp) {
        List<String> parts = new ArrayList<>();
        if (opp.getRequestSummary() != null) {
            parts.add(opp.getRequestSummary());
        }
        if (opp.getDestinations() != null) {
            parts.add("Destinos: " + opp.getDestinations());
        }
        if (opp.getOriginCity() != null) {
            parts.add("Origem: " + opp.getOriginCity());
        }
        if (opp.getTripType() != null) {
            parts.add("Tipo: " + opp.getTripType());
        }
        if (opp.getAdults() != null) {
            parts.add("Adultos: " + opp.getAdults());
        }
        if (opp.getChildrenCount() != null && opp.getChildrenCount() > 0) {
            parts.add("Crianças: " + opp.getChildrenCount()
                    + (opp.getChildrenAges() != null ? " (" + opp.getChildrenAges() + ")" : ""));
        }
        if (opp.getPreferences() != null) {
            parts.add("Preferências: " + opp.getPreferences());
        }
        if (opp.getDesiredServices() != null) {
            parts.add("Serviços: " + opp.getDesiredServices());
        }
        if (opp.getRestrictions() != null) {
            parts.add("Restrições: " + opp.getRestrictions());
        }
        return String.join(" · ", parts);
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

    private static String normalizeLeadSource(String raw) {
        if (raw == null || raw.isBlank()) {
            return "OTHER";
        }
        String v = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (!VALID_LEAD_SOURCES.contains(v)) {
            return "OTHER";
        }
        return v;
    }

    private static String joinList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String joined = values.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(","));
        return joined.isBlank() ? null : joined;
    }

    private static List<String> splitList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizePhone(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        return digits.isBlank() ? null : digits;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }
}
