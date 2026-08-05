package org.example.application.services.agency;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.example.application.dto.agency.AgencyClientDTO;
import org.example.application.dto.agency.ImportAgencyClientsRequest;
import org.example.application.dto.agency.ImportAgencyClientsResponse;
import org.example.application.dto.agency.UpsertAgencyClientRequest;
import org.example.domain.entity.Agency;
import org.example.domain.entity.AgencyClient;
import org.example.domain.entity.AgencyMember;
import org.example.domain.entity.AgencyOpportunity;
import org.example.domain.entity.Trip;
import org.example.domain.entity.User;
import org.example.domain.enums.ContactStatus;
import org.example.domain.repository.AgencyClientRepository;
import org.example.domain.repository.AgencyOpportunityRepository;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
public class AgencyClientService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s.]+\\.[^@\\s]+$");

    @Inject
    AgencyService agencyService;
    @Inject
    AgencyClientRepository clientRepository;
    @Inject
    AgencyOpportunityRepository opportunityRepository;
    @Inject
    TripRepository tripRepository;
    @Inject
    UserRepository userRepository;

    public List<AgencyClientDTO> list(UUID userId, String q, int page, int size) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        return clientRepository.search(member.getAgency().id, q, page, size).stream()
                .map(c -> toDto(c, false))
                .toList();
    }

    public AgencyClientDTO get(UUID userId, UUID clientId) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        AgencyClient client = requireClient(member.getAgency().id, clientId);
        return toDto(client, true);
    }

    @Transactional
    public AgencyClientDTO create(UUID userId, UpsertAgencyClientRequest request) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        validate(request, true);
        String email = normalizeEmail(request.getEmail());
        if (email != null
                && clientRepository.findByAgencyAndEmail(member.getAgency().id, email).isPresent()) {
            throw new BadRequestException("Client with this email already exists");
        }
        AgencyClient client = AgencyClient.builder()
                .agency(member.getAgency())
                .name(request.getName().trim())
                .email(email)
                .phone(blankToNull(request.getPhone()))
                .notes(blankToNull(request.getNotes()))
                .tags(blankToNull(request.getTags()))
                .birthPlace(blankToNull(request.getBirthPlace()))
                .nationality(blankToNull(request.getNationality()))
                .documentNumber(blankToNull(request.getDocumentNumber()))
                .documentType(blankToNull(request.getDocumentType()))
                .documentIssuedAt(parseDate(request.getDocumentIssuedAt()))
                .documentExpiresAt(parseDate(request.getDocumentExpiresAt()))
                .birthDate(parseDate(request.getBirthDate()))
                .gender(blankToNull(request.getGender()))
                .user(resolvePlatformUser(email))
                .contactStatus(ContactStatus.PROSPECT)
                .build();
        clientRepository.persist(client);
        return toDto(client, false);
    }

    @Transactional
    public AgencyClientDTO update(UUID userId, UUID clientId, UpsertAgencyClientRequest request) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        AgencyClient client = requireClient(member.getAgency().id, clientId);
        if (request.getName() != null && !request.getName().isBlank()) {
            client.setName(request.getName().trim());
        }
        if (request.getEmail() != null) {
            String email = normalizeEmail(request.getEmail());
            if (email != null) {
                clientRepository.findByAgencyAndEmail(member.getAgency().id, email)
                        .ifPresent(other -> {
                            if (!other.id.equals(client.id)) {
                                throw new BadRequestException("Client with this email already exists");
                            }
                        });
            }
            client.setEmail(email);
            client.setUser(resolvePlatformUser(email));
        }
        if (request.getPhone() != null) {
            client.setPhone(blankToNull(request.getPhone()));
        }
        if (request.getNotes() != null) {
            client.setNotes(blankToNull(request.getNotes()));
        }
        if (request.getTags() != null) {
            client.setTags(blankToNull(request.getTags()));
        }
        if (request.getBirthPlace() != null) {
            client.setBirthPlace(blankToNull(request.getBirthPlace()));
        }
        if (request.getNationality() != null) {
            client.setNationality(blankToNull(request.getNationality()));
        }
        if (request.getDocumentNumber() != null) {
            client.setDocumentNumber(blankToNull(request.getDocumentNumber()));
        }
        if (request.getDocumentType() != null) {
            client.setDocumentType(blankToNull(request.getDocumentType()));
        }
        if (request.getDocumentIssuedAt() != null) {
            client.setDocumentIssuedAt(parseDate(request.getDocumentIssuedAt()));
        }
        if (request.getDocumentExpiresAt() != null) {
            client.setDocumentExpiresAt(parseDate(request.getDocumentExpiresAt()));
        }
        if (request.getBirthDate() != null) {
            client.setBirthDate(parseDate(request.getBirthDate()));
        }
        if (request.getGender() != null) {
            client.setGender(blankToNull(request.getGender()));
        }
        clientRepository.persist(client);
        return toDto(client, true);
    }

    @Transactional
    public void delete(UUID userId, UUID clientId) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        AgencyClient client = requireClient(member.getAgency().id, clientId);
        List<Trip> trips = tripRepository.findByClientId(client.id);
        for (Trip t : trips) {
            t.setClient(null);
            tripRepository.persist(t);
        }
        clientRepository.delete(client);
    }

    @Transactional
    public ImportAgencyClientsResponse importClients(UUID userId, ImportAgencyClientsRequest request) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        if (request == null || request.getClients() == null || request.getClients().isEmpty()) {
            throw new BadRequestException("clients list is required");
        }
        int created = 0;
        int updated = 0;
        List<UUID> ids = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int index = 0;
        for (ImportAgencyClientsRequest.ImportRow row : request.getClients()) {
            index++;
            try {
                if (row == null || row.getName() == null || row.getName().isBlank()) {
                    errors.add("Linha " + index + ": nome obrigatório");
                    continue;
                }
                String email = normalizeEmail(row.getEmail());
                String phone = blankToNull(row.getPhone());
                if (email == null && phone == null) {
                    errors.add("Linha " + index + ": e-mail ou telefone obrigatório");
                    continue;
                }
                if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
                    errors.add("Linha " + index + ": e-mail inválido");
                    continue;
                }
                AgencyClient existing = null;
                if (email != null) {
                    existing = clientRepository.findByAgencyAndEmail(member.getAgency().id, email).orElse(null);
                }
                if (existing == null && phone != null) {
                    existing = clientRepository
                            .find("agency.id = ?1 AND phone = ?2", member.getAgency().id, phone)
                            .firstResult();
                }
                if (existing != null) {
                    existing.setName(row.getName().trim());
                    if (email != null) {
                        existing.setEmail(email);
                    }
                    if (phone != null) {
                        existing.setPhone(phone);
                    }
                    if (row.getNotes() != null) {
                        existing.setNotes(blankToNull(row.getNotes()));
                    }
                    if (row.getTags() != null) {
                        existing.setTags(blankToNull(row.getTags()));
                    }
                    clientRepository.persist(existing);
                    ids.add(existing.id);
                    updated++;
                } else {
                    AgencyClient client = AgencyClient.builder()
                            .agency(member.getAgency())
                            .name(row.getName().trim())
                            .email(email)
                            .phone(phone)
                            .notes(blankToNull(row.getNotes()))
                            .tags(blankToNull(row.getTags()))
                            .user(resolvePlatformUser(email))
                            .build();
                    clientRepository.persist(client);
                    ids.add(client.id);
                    created++;
                }
            } catch (Exception e) {
                errors.add("Linha " + index + ": " + e.getMessage());
            }
        }
        return ImportAgencyClientsResponse.builder()
                .created(created)
                .updated(updated)
                .clientIds(ids)
                .errors(errors)
                .build();
    }

    private AgencyClient requireClient(UUID agencyId, UUID clientId) {
        AgencyClient client = clientRepository.findById(clientId);
        if (client == null || client.getAgency() == null || !client.getAgency().id.equals(agencyId)) {
            throw new NotFoundException("Client not found");
        }
        return client;
    }

    private void validate(UpsertAgencyClientRequest request, boolean requireAll) {
        if (request == null) {
            throw new BadRequestException("body is required");
        }
        if (requireAll || request.getName() != null) {
            if (request.getName() == null || request.getName().isBlank()) {
                throw new BadRequestException("name is required");
            }
        }
        String email = normalizeEmail(request.getEmail());
        String phone = blankToNull(request.getPhone());
        if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
            throw new BadRequestException("email is invalid");
        }
        if (requireAll && email == null && phone == null) {
            throw new BadRequestException("email or phone is required");
        }
    }

    private static String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /** Vincula à conta Baggagi quando o e-mail já existe na plataforma. */
    private User resolvePlatformUser(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return userRepository.findByEmail(email).orElse(null);
    }

    private AgencyClientDTO toDto(AgencyClient client, boolean includeTrips) {
        List<String> tags = List.of();
        if (client.getTags() != null && !client.getTags().isBlank()) {
            tags = Arrays.stream(client.getTags().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        List<AgencyClientDTO.ClientTripSummaryDTO> trips = null;
        List<AgencyClientDTO.ClientOpportunitySummaryDTO> opportunities = null;
        long tripCount = 0;
        long opportunityCount = 0;
        if (includeTrips) {
            List<Trip> tripEntities = tripRepository.findByClientId(client.id);
            tripCount = tripEntities.size();
            trips = tripEntities.stream()
                    .map(t -> AgencyClientDTO.ClientTripSummaryDTO.builder()
                            .tripId(t.id)
                            .name(t.getName())
                            .proposalStatus(t.getProposalStatus() != null
                                    ? t.getProposalStatus().name() : null)
                            .shareCode(t.getShareCode())
                            .finalPrice(t.getFinalPrice())
                            .updatedAt(t.getUpdatedAt())
                            .build())
                    .toList();
            List<AgencyOpportunity> oppEntities =
                    opportunityRepository.listByClient(client.getAgency().id, client.id);
            opportunityCount = oppEntities.size();
            opportunities = oppEntities.stream()
                    .map(o -> AgencyClientDTO.ClientOpportunitySummaryDTO.builder()
                            .opportunityId(o.id)
                            .title(o.getTitle())
                            .stage(o.getStage() != null ? o.getStage().name() : null)
                            .tripId(o.getTrip() != null ? o.getTrip().id : null)
                            .updatedAt(o.getUpdatedAt())
                            .build())
                    .toList();
        } else {
            tripCount = tripRepository.count("client.id = ?1", client.id);
            opportunityCount = opportunityRepository.count("client.id = ?1", client.id);
        }
        return AgencyClientDTO.builder()
                .id(client.id)
                .name(client.getName())
                .email(client.getEmail())
                .phone(client.getPhone())
                .notes(client.getNotes())
                .tags(tags)
                .userId(client.getUser() != null ? client.getUser().id : null)
                .createdAt(client.getCreatedAt())
                .updatedAt(client.getUpdatedAt())
                .birthPlace(client.getBirthPlace())
                .nationality(client.getNationality())
                .documentNumber(client.getDocumentNumber())
                .documentType(client.getDocumentType())
                .documentIssuedAt(client.getDocumentIssuedAt())
                .documentExpiresAt(client.getDocumentExpiresAt())
                .birthDate(client.getBirthDate())
                .gender(client.getGender())
                .contactStatus(client.getContactStatus() != null
                        ? client.getContactStatus().name()
                        : ContactStatus.PROSPECT.name())
                .trips(trips)
                .tripCount(tripCount)
                .opportunities(opportunities)
                .opportunityCount(opportunityCount)
                .build();
    }

    private static java.time.LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 10) {
            trimmed = trimmed.substring(0, 10);
        }
        try {
            return java.time.LocalDate.parse(trimmed);
        } catch (Exception e) {
            throw new BadRequestException("invalid date: " + value);
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
