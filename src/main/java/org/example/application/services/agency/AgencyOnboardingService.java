package org.example.application.services.agency;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.agency.AgencyDemoSeedResponse;
import org.example.application.services.proposal.ProposalService;
import org.example.domain.entity.Agency;
import org.example.domain.entity.AgencyClient;
import org.example.domain.entity.AgencyMember;
import org.example.domain.entity.Trip;
import org.example.domain.entity.TripProposalTier;
import org.example.domain.entity.User;
import org.example.domain.entity.Workspace;
import org.example.domain.entity.WorkspaceMember;
import org.example.domain.enums.ProposalStatus;
import org.example.domain.enums.TripStatus;
import org.example.domain.enums.WorkspaceRole;
import org.example.domain.repository.AgencyClientRepository;
import org.example.domain.repository.AgencyRepository;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@ApplicationScoped
public class AgencyOnboardingService {

    @Inject
    AgencyService agencyService;
    @Inject
    AgencyRepository agencyRepository;
    @Inject
    AgencyClientRepository clientRepository;
    @Inject
    TripRepository tripRepository;
    @Inject
    UserRepository userRepository;

    @Transactional
    public AgencyDemoSeedResponse seedDemo(UUID userId) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        Agency agency = member.getAgency();
        User creator = userRepository.findById(userId);
        if (creator == null) {
            throw new NotFoundException("User not found");
        }

        // Remove previous demo before seeding again
        clearDemoInternal(agency);

        AgencyClient client = AgencyClient.builder()
                .agency(agency)
                .name("Mariana Silva (Exemplo)")
                .email("exemplo.mariana@baggagi.demo")
                .phone("5511987654321")
                .notes("Cliente de demonstração do onboarding — pode apagar com segurança.")
                .tags("exemplo,demo")
                .demo(true)
                .build();
        clientRepository.persist(client);

        Workspace workspace = resolveWorkspace(creator);

        BigDecimal baseCost = new BigDecimal("8000.00");
        BigDecimal markup = agency.getMarkupPercentage() != null
                ? agency.getMarkupPercentage()
                : new BigDecimal("12");
        BigDecimal finalPrice = baseCost
                .multiply(BigDecimal.ONE.add(markup.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)))
                .add(new BigDecimal("300"))
                .setScale(2, RoundingMode.HALF_UP);

        LocalDate start = LocalDate.now().plusMonths(2);
        LocalDate end = start.plusDays(6);

        Trip trip = Trip.builder()
                .name("Lisboa — proposta exemplo")
                .description("Viagem de demonstração gerada no onboarding. Destino: Lisboa. "
                        + "Edite ou apague os dados de exemplo quando quiser.")
                .workspace(workspace)
                .createdBy(creator)
                .agency(agency)
                .client(client)
                .assignedConsultant(creator)
                .status(TripStatus.PLANNING)
                .proposalStatus(ProposalStatus.QUOTING)
                .shareCode(ProposalService.generateShareCode())
                .startDate(start)
                .endDate(end)
                .durationDays(7)
                .baseCost(baseCost)
                .finalPrice(finalPrice)
                .currency("BRL")
                .proposalClientEmail(client.getEmail())
                .proposalClientName(client.getName())
                .demo(true)
                .segments(new ArrayList<>())
                .proposalTiers(new ArrayList<>())
                .build();
        tripRepository.persist(trip);

        TripProposalTier economy = TripProposalTier.builder()
                .trip(trip)
                .code("ECONOMY")
                .label("Opção econômica (Exemplo)")
                .priceDelta(new BigDecimal("-800.00"))
                .sortOrder(0)
                .build();
        TripProposalTier premium = TripProposalTier.builder()
                .trip(trip)
                .code("PREMIUM")
                .label("Opção premium (Exemplo)")
                .priceDelta(new BigDecimal("1200.00"))
                .sortOrder(1)
                .build();
        trip.getProposalTiers().add(economy);
        trip.getProposalTiers().add(premium);

        agency.setDemoDataActive(true);
        agency.setOnboardingClientId(client.id);
        agency.setOnboardingTripId(trip.id);
        agency.setOnboardingStep("BUILD");
        agencyRepository.persist(agency);

        log.info("Seeded demo client={} trip={} for agency={}", client.id, trip.id, agency.id);

        return AgencyDemoSeedResponse.builder()
                .clientId(client.id)
                .tripId(trip.id)
                .shareCode(trip.getShareCode())
                .clientName(client.getName())
                .tripName(trip.getName())
                .build();
    }

    @Transactional
    public void clearDemo(UUID userId) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        clearDemoInternal(member.getAgency());
    }

    private void clearDemoInternal(Agency agency) {
        List<Trip> demoTrips = tripRepository.list(
                "agency.id = ?1 AND demo = true", agency.id);
        for (Trip t : demoTrips) {
            tripRepository.delete(t);
        }
        List<AgencyClient> demoClients = clientRepository.list(
                "agency.id = ?1 AND demo = true", agency.id);
        for (AgencyClient c : demoClients) {
            clientRepository.delete(c);
        }
        agency.setDemoDataActive(false);
        if (agency.getOnboardingTripId() != null) {
            Trip still = tripRepository.findById(agency.getOnboardingTripId());
            if (still == null || still.isDemo()) {
                agency.setOnboardingTripId(null);
            }
        }
        if (agency.getOnboardingClientId() != null) {
            AgencyClient still = clientRepository.findById(agency.getOnboardingClientId());
            if (still == null || still.isDemo()) {
                agency.setOnboardingClientId(null);
            }
        }
        agencyRepository.persist(agency);
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
}
