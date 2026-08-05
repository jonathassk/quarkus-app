package org.example.application.services.agency;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.example.application.dto.agency.AgencyOpportunityDTO;
import org.example.application.dto.agency.UpsertAgencyOpportunityRequest;
import org.example.application.services.proposal.ProposalService;
import org.example.domain.entity.Agency;
import org.example.domain.entity.AgencyClient;
import org.example.domain.entity.AgencyMember;
import org.example.domain.entity.AgencyOpportunity;
import org.example.domain.entity.Trip;
import org.example.domain.entity.User;
import org.example.domain.entity.Workspace;
import org.example.domain.entity.WorkspaceMember;
import org.example.domain.enums.ContactStatus;
import org.example.domain.enums.OpportunityStage;
import org.example.domain.enums.ProposalStatus;
import org.example.domain.enums.QualificationStatus;
import org.example.domain.enums.TripStatus;
import org.example.domain.enums.WorkspaceRole;
import org.example.domain.repository.AgencyClientRepository;
import org.example.domain.repository.AgencyOpportunityRepository;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;

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
    AgencyClientRepository clientRepository;
    @Inject
    TripRepository tripRepository;
    @Inject
    UserRepository userRepository;

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
        return opportunityRepository
                .search(member.getAgency().id, parsed, consultantId, clientId, q, page, size)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public AgencyOpportunityDTO get(UUID userId, UUID opportunityId) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        return toDto(requireOpportunity(member.getAgency().id, opportunityId));
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
                .leadSource(leadSource)
                .leadSourceDetail(blankToNull(request.getLeadSourceDetail()))
                .build();
        applyDetails(opp, request);
        refreshQualification(opp);
        opportunityRepository.persist(opp);
        return toDto(opp);
    }

    @Transactional
    public AgencyOpportunityDTO update(UUID userId, UUID opportunityId, UpsertAgencyOpportunityRequest request) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        AgencyOpportunity opp = requireOpportunity(member.getAgency().id, opportunityId);
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
        if (request.getAssignedConsultantId() != null) {
            opp.setAssignedConsultant(
                    resolveConsultant(member.getAgency().id, request.getAssignedConsultantId(), userId));
        }
        if (request.getNextFollowUpAt() != null) {
            opp.setNextFollowUpAt(request.getNextFollowUpAt());
        }
        if (request.getStage() != null && !request.getStage().isBlank()) {
            applyStage(opp, OpportunityStage.fromString(request.getStage()), request.getLostReason());
        }
        applyDetails(opp, request);
        refreshQualification(opp);
        opportunityRepository.persist(opp);
        return toDto(opp);
    }

    @Transactional
    public AgencyOpportunityDTO markLost(UUID userId, UUID opportunityId, String reason) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        AgencyOpportunity opp = requireOpportunity(member.getAgency().id, opportunityId);
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("lostReason is required");
        }
        applyStage(opp, OpportunityStage.LOST, reason.trim());
        opportunityRepository.persist(opp);
        return toDto(opp);
    }

    @Transactional
    public AgencyOpportunityDTO markWon(UUID userId, UUID opportunityId) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        AgencyOpportunity opp = requireOpportunity(member.getAgency().id, opportunityId);
        applyStage(opp, OpportunityStage.WON, null);
        opportunityRepository.persist(opp);
        return toDto(opp);
    }

    /**
     * Converte a solicitação em proposta (Trip) sem redigitar dados.
     */
    @Transactional
    public AgencyOpportunityDTO convertToProposal(UUID userId, UUID opportunityId) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        AgencyOpportunity opp = requireOpportunity(member.getAgency().id, opportunityId);
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
                .build();
        tripRepository.persist(trip);

        opp.setTrip(trip);
        if (opp.getStage() == OpportunityStage.NEW
                || opp.getStage() == OpportunityStage.QUALIFYING) {
            opp.setStage(OpportunityStage.QUOTING);
        }
        refreshQualification(opp);
        opportunityRepository.persist(opp);
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

    private AgencyOpportunityDTO toDto(AgencyOpportunity opp) {
        AgencyClient client = opp.getClient();
        User consultant = opp.getAssignedConsultant();
        Trip trip = opp.getTrip();
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
                .lostReason(opp.getLostReason())
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
