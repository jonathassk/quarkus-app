package org.example.application.services.passenger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.application.dto.passenger.*;
import org.example.application.services.B2bAuditService;
import org.example.domain.entity.*;
import org.example.domain.enums.*;
import org.example.domain.repository.*;
import org.example.infrastructure.crypto.DocumentCryptoService;
import org.example.infrastructure.email.EmailWorkerInvoker;
import org.example.infrastructure.storage.ObjectStorageService;
import org.example.utils.DocumentUploadSupport;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class TripPassengerService {

    private static final char[] TOKEN_ALPHABET =
            "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TripRepository tripRepository;
    private final TripPassengerRepository passengerRepository;
    private final TripDocumentRepository tripDocumentRepository;
    private final AgencyOpportunityRepository opportunityRepository;
    private final AgencyMemberRepository agencyMemberRepository;
    private final AgencyClientRepository agencyClientRepository;
    private final PassengerFieldCorrectionRepository correctionRepository;
    private final UserRepository userRepository;
    private final B2bAuditService auditService;
    private final EmailWorkerInvoker emailWorkerInvoker;
    private final ObjectStorageService objectStorageService;
    private final DocumentCryptoService documentCryptoService;
    private final ObjectMapper objectMapper;

    @ConfigProperty(name = "app.public-url", defaultValue = "http://localhost:3000")
    String publicUrl;

    public void requireTripAccess(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findById(tripId);
        if (trip == null) {
            throw new NotFoundException("Trip not found");
        }
        if (tripRepository.isUserLinkedToTrip(tripId, userId)) {
            return;
        }
        if (trip.getAgency() != null) {
            var memberOpt = agencyMemberRepository.findByAgencyAndUser(trip.getAgency().id, userId);
            if (memberOpt.isPresent()) {
                AgencyMember member = memberOpt.get();
                if (member.getAgencyRole() == AgencyRole.AGENCY_OWNER) {
                    return;
                }
                if (trip.getAssignedConsultant() != null
                        && trip.getAssignedConsultant().id.equals(userId)) {
                    return;
                }
                if (trip.getCreatedBy() != null && trip.getCreatedBy().id.equals(userId)) {
                    return;
                }
            }
        }
        throw new ForbiddenException("No access to this trip");
    }

    public List<TripPassengerResponse> list(UUID tripId) {
        return passengerRepository.findByTripId(tripId).stream().map(this::toAgentResponse).toList();
    }

    @Transactional
    public TripPassengerResponse create(UUID tripId, CreateTripPassengerRequest req, UUID actorId) {
        Trip trip = requireTrip(tripId);
        TripPassenger p = TripPassenger.builder()
                .trip(trip)
                .displayName(blankToNull(req != null ? req.getDisplayName() : null))
                .passengerType(PassengerType.fromString(req != null ? req.getPassengerType() : null))
                .primaryContact(Boolean.TRUE.equals(req != null ? req.getPrimaryContact() : null))
                .email(blankToNull(req != null ? req.getEmail() : null))
                .whatsapp(blankToNull(req != null ? req.getWhatsapp() : null))
                .phone(blankToNull(req != null ? req.getPhone() : null))
                .nationality(blankToNull(req != null ? req.getNationality() : null))
                .birthDate(req != null ? req.getBirthDate() : null)
                .formStatus(PassengerFormStatus.NOT_REQUESTED)
                .sortOrder(passengerRepository.nextSortOrder(tripId))
                .build();

        if (req != null && req.getSourceClientId() != null) {
            AgencyClient client = agencyClientRepository.findById(req.getSourceClientId());
            if (client != null) {
                p.setSourceClient(client);
            }
        }
        if (req != null && req.getGuardianPassengerId() != null) {
            passengerRepository.findByIdAndTripId(req.getGuardianPassengerId(), tripId)
                    .ifPresent(p::setGuardian);
        }
        if (p.isPrimaryContact()) {
            clearOtherPrimaryContacts(tripId, null);
        }
        passengerRepository.persist(p);
        auditService.record(
                trip, actorId, B2bTripLogAction.PASSENGER_CREATED, "PASSENGER", p.id,
                "Passageiro criado: " + label(p));
        return toAgentResponse(p);
    }

    @Transactional
    public TripPassengerResponse update(
            UUID tripId, UUID passengerId, UpdateTripPassengerRequest req, UUID actorId) {
        TripPassenger p = requirePassenger(tripId, passengerId);
        if (req == null) {
            return toAgentResponse(p);
        }
        if (req.getDisplayName() != null) {
            p.setDisplayName(blankToNull(req.getDisplayName()));
        }
        if (req.getPassengerType() != null) {
            p.setPassengerType(PassengerType.fromString(req.getPassengerType()));
        }
        if (req.getPrimaryContact() != null) {
            if (Boolean.TRUE.equals(req.getPrimaryContact())) {
                clearOtherPrimaryContacts(tripId, p.id);
                p.setPrimaryContact(true);
            } else {
                p.setPrimaryContact(false);
            }
        }
        if (req.getEmail() != null) {
            p.setEmail(blankToNull(req.getEmail()));
        }
        if (req.getWhatsapp() != null) {
            p.setWhatsapp(blankToNull(req.getWhatsapp()));
        }
        if (req.getPhone() != null) {
            p.setPhone(blankToNull(req.getPhone()));
        }
        if (req.getNationality() != null) {
            p.setNationality(blankToNull(req.getNationality()));
        }
        if (req.getBirthDate() != null) {
            p.setBirthDate(req.getBirthDate());
        }
        if (req.getDocumentType() != null) {
            p.setDocumentType(blankToNull(req.getDocumentType()));
        }
        if (req.getDocumentNumber() != null) {
            p.setDocumentNumber(blankToNull(req.getDocumentNumber()));
        }
        if (req.getDocumentExpiresAt() != null) {
            p.setDocumentExpiresAt(req.getDocumentExpiresAt());
        }
        if (req.getFormStatus() != null) {
            p.setFormStatus(PassengerFormStatus.fromString(req.getFormStatus()));
        }
        if (req.getFormPayload() != null) {
            p.setFormPayload(mergePayload(p.getFormPayload(), req.getFormPayload()));
        }
        if (req.getNotes() != null) {
            p.setNotes(blankToNull(req.getNotes()));
        }
        if (req.getSourceClientId() != null) {
            AgencyClient client = agencyClientRepository.findById(req.getSourceClientId());
            p.setSourceClient(client);
        }
        if (req.getGuardianPassengerId() != null) {
            if (req.getGuardianPassengerId().equals(p.id)) {
                throw new BadRequestException("Passenger cannot be their own guardian");
            }
            passengerRepository.findByIdAndTripId(req.getGuardianPassengerId(), tripId)
                    .ifPresentOrElse(p::setGuardian, () -> {
                        throw new BadRequestException("Guardian passenger not found");
                    });
        }
        passengerRepository.persist(p);
        auditService.record(
                p.getTrip(), actorId, B2bTripLogAction.PASSENGER_UPDATED, "PASSENGER", p.id,
                "Passageiro atualizado: " + label(p));
        return toAgentResponse(p);
    }

    @Transactional
    public void delete(UUID tripId, UUID passengerId, UUID actorId) {
        TripPassenger p = requirePassenger(tripId, passengerId);
        String name = label(p);
        Trip trip = p.getTrip();
        passengerRepository.delete(p);
        auditService.record(
                trip, actorId, B2bTripLogAction.PASSENGER_DELETED, "PASSENGER", passengerId,
                "Passageiro removido: " + name);
    }

    /**
     * Idempotente: se já existem passageiros, não recria.
     * Seed a partir da oportunidade (adults/children/infants) + cliente CRM.
     */
    @Transactional
    public List<TripPassengerResponse> seedFromOpportunity(UUID tripId, UUID actorId) {
        Trip trip = requireTrip(tripId);
        if (passengerRepository.countByTripId(tripId) > 0) {
            return list(tripId);
        }

        AgencyOpportunity opp = opportunityRepository.findByTripId(tripId).orElse(null);
        int adults = opp != null && opp.getAdults() != null ? Math.max(0, opp.getAdults()) : 1;
        int children = opp != null && opp.getChildrenCount() != null ? Math.max(0, opp.getChildrenCount()) : 0;
        int infants = opp != null && opp.getInfants() != null ? Math.max(0, opp.getInfants()) : 0;
        if (adults + children + infants == 0) {
            adults = 1;
        }

        AgencyClient client = trip.getClient();
        if (client == null && opp != null) {
            client = opp.getClient();
        }
        boolean clientIsPassenger = opp == null || !Boolean.FALSE.equals(opp.getPassenger());

        int order = 0;
        TripPassenger primaryAdult = null;
        for (int i = 0; i < adults; i++) {
            boolean lead = i == 0;
            String name = null;
            AgencyClient source = null;
            String email = null;
            String phone = null;
            if (lead && clientIsPassenger && client != null) {
                name = client.getName();
                source = client;
                email = client.getEmail();
                phone = client.getPhone();
            }
            TripPassenger p = TripPassenger.builder()
                    .trip(trip)
                    .displayName(name)
                    .sourceClient(source)
                    .email(email)
                    .phone(phone)
                    .whatsapp(phone)
                    .passengerType(PassengerType.ADULT)
                    .primaryContact(lead)
                    .formStatus(PassengerFormStatus.NOT_REQUESTED)
                    .sortOrder(order++)
                    .build();
            if (lead && client != null && clientIsPassenger) {
                p.setNationality(client.getNationality());
                p.setBirthDate(client.getBirthDate());
                p.setDocumentType(client.getDocumentType());
                p.setDocumentNumber(client.getDocumentNumber());
                p.setDocumentExpiresAt(client.getDocumentExpiresAt());
            }
            passengerRepository.persist(p);
            if (lead) {
                primaryAdult = p;
            }
            if (actorId != null) {
                auditService.record(
                        trip, actorId, B2bTripLogAction.PASSENGER_CREATED, "PASSENGER", p.id,
                        "Passageiro seed: " + label(p));
            }
        }
        for (int i = 0; i < children; i++) {
            TripPassenger p = TripPassenger.builder()
                    .trip(trip)
                    .passengerType(PassengerType.CHILD)
                    .guardian(primaryAdult)
                    .formStatus(PassengerFormStatus.NOT_REQUESTED)
                    .sortOrder(order++)
                    .build();
            passengerRepository.persist(p);
        }
        for (int i = 0; i < infants; i++) {
            TripPassenger p = TripPassenger.builder()
                    .trip(trip)
                    .passengerType(PassengerType.INFANT)
                    .guardian(primaryAdult)
                    .formStatus(PassengerFormStatus.NOT_REQUESTED)
                    .sortOrder(order++)
                    .build();
            passengerRepository.persist(p);
        }
        return list(tripId);
    }

    /** Seed sem ator autenticado (hook de aprovação). */
    @Transactional
    public void seedFromOpportunityQuiet(UUID tripId) {
        try {
            seedFromOpportunity(tripId, null);
        } catch (Exception e) {
            log.warn("Passenger seed failed tripId={}: {}", tripId, e.getMessage());
        }
    }

    @Transactional
    public PassengerInviteResponse invite(UUID tripId, UUID passengerId, UUID actorId) {
        TripPassenger p = requirePassenger(tripId, passengerId);
        if (p.getPassengerType() != PassengerType.ADULT) {
            throw new BadRequestException(
                    "Menores não recebem link próprio — o responsável preenche no formulário");
        }
        String email = p.getEmail();
        if (p.getInviteToken() == null || p.getInviteToken().isBlank()) {
            p.setInviteToken(generateUniqueToken());
        }
        p.setInviteSentAt(Instant.now());
        if (p.getFormStatus() == PassengerFormStatus.NOT_REQUESTED
                || p.getFormStatus() == PassengerFormStatus.INVITED) {
            p.setFormStatus(PassengerFormStatus.INVITED);
        }
        passengerRepository.persist(p);

        String url = formUrl(p.getInviteToken());
        String tripName = p.getTrip().getName() != null ? p.getTrip().getName() : "viagem";
        String emailSentTo = null;
        if (email != null && !email.isBlank()) {
            String subject = "Formulário de passageiro — " + tripName;
            String text =
                    "Olá"
                            + (p.getDisplayName() != null ? ", " + p.getDisplayName() : "")
                            + "!\n\n"
                            + "Sua agência pediu que você preencha os dados para a viagem \""
                            + tripName
                            + "\".\n\n"
                            + "Abra o link (válido enquanto a viagem estiver ativa):\n"
                            + url
                            + "\n\nVocê pode salvar e continuar depois.";
            String html =
                    "<p>Olá"
                            + (p.getDisplayName() != null
                                    ? ", <strong>" + escapeHtml(p.getDisplayName()) + "</strong>"
                                    : "")
                            + "!</p>"
                            + "<p>Preencha o formulário de passageiro da viagem <strong>"
                            + escapeHtml(tripName)
                            + "</strong>.</p>"
                            + "<p><a href=\""
                            + url
                            + "\">Abrir formulário</a></p>"
                            + "<p>Você pode salvar e continuar depois.</p>";
            emailWorkerInvoker.enqueueDirectEmail(email.trim(), subject, text, html);
            emailSentTo = email.trim();
        }

        auditService.record(
                p.getTrip(),
                actorId,
                B2bTripLogAction.PASSENGER_INVITED,
                "PASSENGER",
                p.id,
                emailSentTo != null
                        ? "Formulário enviado para " + emailSentTo
                        : "Link do formulário gerado");
        return PassengerInviteResponse.builder()
                .inviteToken(p.getInviteToken())
                .inviteUrl(url)
                .emailSentTo(emailSentTo)
                .build();
    }

    @Transactional
    public TripPassengerResponse markReviewed(UUID tripId, UUID passengerId, UUID actorId) {
        TripPassenger p = requirePassenger(tripId, passengerId);
        p.setFormStatus(PassengerFormStatus.COMPLETE);
        passengerRepository.persist(p);
        tripDocumentRepository.findLatestByPassengerId(p.id).ifPresent(doc -> {
            doc.setDocReviewStatus(PassengerDocReviewStatus.VALID);
            tripDocumentRepository.persist(doc);
        });
        auditService.record(
                p.getTrip(), actorId, B2bTripLogAction.PASSENGER_MARKED_REVIEWED, "PASSENGER", p.id,
                "Passageiro marcado como conferido: " + label(p));
        return toAgentResponse(p);
    }

    @Transactional
    public TripPassengerResponse copyFromClient(UUID tripId, UUID passengerId, UUID actorId) {
        TripPassenger p = requirePassenger(tripId, passengerId);
        Trip trip = p.getTrip();
        AgencyClient client = trip.getClient();
        if (client == null) {
            AgencyOpportunity opp = opportunityRepository.findByTripId(tripId).orElse(null);
            if (opp != null) {
                client = opp.getClient();
            }
        }
        if (client == null) {
            throw new BadRequestException("Viagem sem cliente CRM vinculado");
        }
        p.setSourceClient(client);
        p.setDisplayName(client.getName());
        p.setEmail(client.getEmail());
        p.setPhone(client.getPhone());
        if (p.getWhatsapp() == null) {
            p.setWhatsapp(client.getPhone());
        }
        p.setNationality(client.getNationality());
        p.setBirthDate(client.getBirthDate());
        p.setDocumentType(client.getDocumentType());
        p.setDocumentNumber(client.getDocumentNumber());
        p.setDocumentExpiresAt(client.getDocumentExpiresAt());
        passengerRepository.persist(p);
        auditService.record(
                trip, actorId, B2bTripLogAction.PASSENGER_UPDATED, "PASSENGER", p.id,
                "Dados copiados do cliente CRM");
        return toAgentResponse(p);
    }

    @Transactional
    public PassengerCorrectionResponse requestCorrection(
            UUID tripId, UUID passengerId, RequestPassengerCorrectionRequest req, UUID actorId) {
        if (req == null || req.getFieldName() == null || req.getFieldName().isBlank()) {
            throw new BadRequestException("fieldName is required");
        }
        TripPassenger p = requirePassenger(tripId, passengerId);
        String field = req.getFieldName().trim();
        String oldVal = readFieldValue(p, field);
        User requester = actorId != null ? userRepository.findById(actorId) : null;

        PassengerFieldCorrection correction = PassengerFieldCorrection.builder()
                .passenger(p)
                .trip(p.getTrip())
                .fieldName(field)
                .oldValue(oldVal)
                .expectedValue(blankToNull(req.getExpectedValue()))
                .agentNote(blankToNull(req.getAgentNote()))
                .status(PassengerCorrectionStatus.OPEN)
                .requestedBy(requester)
                .requestedAt(Instant.now())
                .build();
        correctionRepository.persist(correction);

        p.setFormStatus(PassengerFormStatus.CORRECTION_REQUESTED);
        passengerRepository.persist(p);

        String prevJson = "{\"" + field + "\":\"" + escJson(oldVal) + "\"}";
        String newJson =
                "{\""
                        + field
                        + "\":\""
                        + escJson(req.getExpectedValue())
                        + "\",\"correctionId\":\""
                        + correction.id
                        + "\"}";
        auditService.record(
                p.getTrip(),
                actorId,
                B2bTripLogAction.PASSENGER_CORRECTION_REQUESTED,
                "PASSENGER",
                p.id,
                prevJson,
                newJson,
                "Correção solicitada no campo " + field,
                null);
        return toCorrectionResponse(correction);
    }

    @Transactional
    public PassengerCorrectionResponse resolveCorrectionByAgent(
            UUID tripId, UUID passengerId, UUID correctionId, ResolvePassengerCorrectionRequest req, UUID actorId) {
        TripPassenger p = requirePassenger(tripId, passengerId);
        PassengerFieldCorrection c =
                correctionRepository
                        .findOpenByIdAndPassenger(correctionId, passengerId)
                        .orElseThrow(() -> new NotFoundException("Correction not found"));
        return resolveCorrection(p, c, req != null ? req.getCorrectedValue() : null, "agent", actorId);
    }

    @Transactional
    public PassengerCorrectionResponse resolveCorrectionByToken(
            String token, UUID correctionId, ResolvePassengerCorrectionRequest req) {
        TripPassenger p = requireByToken(token);
        PassengerFieldCorrection c =
                correctionRepository
                        .findOpenByIdAndPassenger(correctionId, p.id)
                        .orElseThrow(() -> new NotFoundException("Correction not found"));
        String value = req != null ? req.getCorrectedValue() : null;
        if (value == null || value.isBlank()) {
            throw new BadRequestException("correctedValue is required");
        }
        return resolveCorrection(p, c, value, label(p), null);
    }

    private PassengerCorrectionResponse resolveCorrection(
            TripPassenger p,
            PassengerFieldCorrection c,
            String correctedValue,
            String resolvedByLabel,
            UUID actorId) {
        String oldVal = c.getOldValue();
        String applied = correctedValue != null ? correctedValue.trim() : c.getExpectedValue();
        if (applied != null && !applied.isBlank()) {
            applyFieldValue(p, c.getFieldName(), applied);
            passengerRepository.persist(p);
        }
        c.setCorrectedValue(applied);
        c.setStatus(PassengerCorrectionStatus.RESOLVED);
        c.setResolvedAt(Instant.now());
        c.setResolvedByLabel(resolvedByLabel);
        correctionRepository.persist(c);

        if (correctionRepository.countOpenByPassengerId(p.id) == 0) {
            p.setFormStatus(PassengerFormStatus.SUBMITTED);
            p.setSubmittedAt(Instant.now());
            passengerRepository.persist(p);
        }

        String prevJson = "{\"" + c.getFieldName() + "\":\"" + escJson(oldVal) + "\"}";
        String newJson = "{\"" + c.getFieldName() + "\":\"" + escJson(applied) + "\"}";
        auditService.record(
                p.getTrip(),
                actorId,
                B2bTripLogAction.PASSENGER_CORRECTION_RESOLVED,
                "PASSENGER",
                p.id,
                prevJson,
                newJson,
                "Campo " + c.getFieldName() + " corrigido",
                null);
        return toCorrectionResponse(c);
    }

    public List<PassengerCorrectionResponse> listCorrections(UUID tripId, UUID passengerId) {
        requirePassenger(tripId, passengerId);
        return correctionRepository.findByPassengerId(passengerId).stream()
                .map(this::toCorrectionResponse)
                .toList();
    }

    @Transactional
    public TripPassengerResponse applyReusableProfile(UUID tripId, UUID passengerId, UUID actorId) {
        TripPassenger p = requirePassenger(tripId, passengerId);
        AgencyClient client = findConsentedClientForPassenger(p);
        if (client == null || client.getTravelerReuseConsentAt() == null) {
            throw new BadRequestException("Nenhum perfil autorizado nesta agência");
        }
        p.setSourceClient(client);
        p.setDisplayName(client.getName());
        p.setEmail(client.getEmail());
        p.setPhone(client.getPhone());
        p.setWhatsapp(client.getPhone());
        p.setNationality(client.getNationality());
        p.setBirthDate(client.getBirthDate());
        p.setDocumentType(client.getDocumentType());
        p.setDocumentNumber(client.getDocumentNumber());
        p.setDocumentExpiresAt(client.getDocumentExpiresAt());
        if (client.getTravelerPrefsJson() != null) {
            p.setFormPayload(mergePayload(p.getFormPayload(), client.getTravelerPrefsJson()));
        }
        passengerRepository.persist(p);
        auditService.record(
                p.getTrip(), actorId, B2bTripLogAction.PASSENGER_UPDATED, "PASSENGER", p.id,
                "Perfil reutilizado com consentimento da agência");
        return toAgentResponse(p);
    }

    // ── Public form ──────────────────────────────────────────────────────────

    public PublicPassengerFormResponse getPublicForm(String token) {
        TripPassenger p = requireByToken(token);
        return toPublicResponse(p);
    }

    @Transactional
    public PublicPassengerFormResponse patchPublicForm(String token, PatchPublicPassengerFormRequest req) {
        TripPassenger p = requireByToken(token);
        if (isLocked(p)) {
            throw new ForbiddenException("Formulário já enviado e em revisão");
        }
        if (req == null) {
            return toPublicResponse(p);
        }
        if (req.getDisplayName() != null) {
            p.setDisplayName(blankToNull(req.getDisplayName()));
        }
        if (req.getPassengerType() != null) {
            p.setPassengerType(PassengerType.fromString(req.getPassengerType()));
        }
        if (req.getEmail() != null) {
            p.setEmail(blankToNull(req.getEmail()));
        }
        if (req.getWhatsapp() != null) {
            p.setWhatsapp(blankToNull(req.getWhatsapp()));
        }
        if (req.getNationality() != null) {
            p.setNationality(blankToNull(req.getNationality()));
        }
        if (req.getBirthDate() != null) {
            p.setBirthDate(req.getBirthDate());
        }
        if (req.getDocumentType() != null) {
            p.setDocumentType(blankToNull(req.getDocumentType()));
        }
        if (req.getDocumentNumber() != null) {
            p.setDocumentNumber(blankToNull(req.getDocumentNumber()));
        }
        if (req.getDocumentExpiresAt() != null) {
            p.setDocumentExpiresAt(req.getDocumentExpiresAt());
        }
        if (req.getFormPayload() != null) {
            p.setFormPayload(mergePayload(p.getFormPayload(), req.getFormPayload()));
        }
        if (p.getFormStatus() == PassengerFormStatus.NOT_REQUESTED
                || p.getFormStatus() == PassengerFormStatus.INVITED) {
            p.setFormStatus(PassengerFormStatus.IN_PROGRESS);
        }
        passengerRepository.persist(p);
        return toPublicResponse(p);
    }

    @Transactional
    public PublicPassengerFormResponse submitPublicForm(
            String token, SubmitPublicPassengerFormRequest body) {
        TripPassenger p = requireByToken(token);
        if (isLocked(p) && p.getFormStatus() != PassengerFormStatus.CORRECTION_REQUESTED) {
            throw new ForbiddenException("Formulário já enviado");
        }
        if (p.getDisplayName() == null || p.getDisplayName().isBlank()) {
            throw new BadRequestException("Nome é obrigatório");
        }
        p.setFormStatus(PassengerFormStatus.SUBMITTED);
        p.setSubmittedAt(Instant.now());
        passengerRepository.persist(p);

        if (p.isPrimaryContact()) {
            for (TripPassenger other : passengerRepository.findByTripId(p.getTrip().id)) {
                if (other.getGuardian() != null
                        && other.getGuardian().id.equals(p.id)
                        && other.getFormStatus() != PassengerFormStatus.COMPLETE
                        && other.getDisplayName() != null
                        && !other.getDisplayName().isBlank()) {
                    other.setFormStatus(PassengerFormStatus.SUBMITTED);
                    other.setSubmittedAt(Instant.now());
                    passengerRepository.persist(other);
                }
            }
        }

        if (body != null && Boolean.TRUE.equals(body.getSaveToAgencyProfile())) {
            saveTravelerProfileConsent(p);
        }

        auditService.record(
                p.getTrip(), null, B2bTripLogAction.PASSENGER_FORM_SUBMITTED, "PASSENGER", p.id,
                "Formulário enviado pelo passageiro: " + label(p));
        return toPublicResponse(p);
    }

    /** Compat: submit sem body. */
    @Transactional
    public PublicPassengerFormResponse submitPublicForm(String token) {
        return submitPublicForm(token, null);
    }

    private void saveTravelerProfileConsent(TripPassenger p) {
        Trip trip = p.getTrip();
        if (trip.getAgency() == null) {
            return;
        }
        AgencyClient client = p.getSourceClient();
        if (client == null) {
            client = trip.getClient();
        }
        if (client == null && p.getEmail() != null && !p.getEmail().isBlank()) {
            client = agencyClientRepository
                    .find("agency.id = ?1 AND lower(email) = ?2",
                            trip.getAgency().id, p.getEmail().trim().toLowerCase(Locale.ROOT))
                    .firstResult();
        }
        if (client == null) {
            client = AgencyClient.builder()
                    .agency(trip.getAgency())
                    .name(p.getDisplayName() != null ? p.getDisplayName() : "Passageiro")
                    .email(p.getEmail())
                    .phone(p.getWhatsapp() != null ? p.getWhatsapp() : p.getPhone())
                    .build();
            agencyClientRepository.persist(client);
            p.setSourceClient(client);
        }
        if (p.getDisplayName() != null) {
            client.setName(p.getDisplayName());
        }
        if (p.getEmail() != null) {
            client.setEmail(p.getEmail());
        }
        if (p.getWhatsapp() != null || p.getPhone() != null) {
            client.setPhone(p.getWhatsapp() != null ? p.getWhatsapp() : p.getPhone());
        }
        client.setNationality(p.getNationality());
        client.setBirthDate(p.getBirthDate());
        client.setDocumentType(p.getDocumentType());
        client.setDocumentNumber(p.getDocumentNumber());
        client.setDocumentExpiresAt(p.getDocumentExpiresAt());
        client.setTravelerReuseConsentAt(Instant.now());
        // Salva só preferências/necessidades do payload
        try {
            if (p.getFormPayload() != null) {
                JsonNode node = objectMapper.readTree(p.getFormPayload());
                ObjectNode prefs = objectMapper.createObjectNode();
                if (node.has("preferences")) {
                    prefs.set("preferences", node.get("preferences"));
                }
                if (node.has("needs")) {
                    prefs.set("needs", node.get("needs"));
                }
                if (!prefs.isEmpty()) {
                    client.setTravelerPrefsJson(objectMapper.writeValueAsString(prefs));
                }
            }
        } catch (Exception ignored) {
            // keep previous prefs
        }
        agencyClientRepository.persist(client);
        passengerRepository.persist(p);
        auditService.record(
                trip,
                null,
                B2bTripLogAction.PASSENGER_PROFILE_CONSENT,
                "PASSENGER",
                p.id,
                null,
                "{\"agencyClientId\":\"" + client.id + "\"}",
                "Consentimento de reuso do perfil na agência",
                null);
    }

    @Transactional
    public TripDocument uploadPublicDocument(
            String token, byte[] fileBytes, String fileName, String contentType, String title, String kind) {
        TripPassenger p = requireByToken(token);
        if (isLocked(p)) {
            throw new ForbiddenException("Formulário já enviado");
        }
        if (!objectStorageService.isConfigured() || !documentCryptoService.isConfigured()) {
            throw new IllegalStateException("Document storage not configured");
        }
        var resolved = DocumentUploadSupport.resolve(fileName, contentType);
        if (resolved.isEmpty()) {
            throw new BadRequestException(
                    DocumentUploadSupport.unsupportedTypeMessage(contentType, fileName));
        }
        DocumentUploadSupport.ResolvedUpload upload = resolved.get();
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BadRequestException("Empty file");
        }
        if (fileBytes.length > DocumentUploadSupport.MAX_UPLOAD_BYTES) {
            throw new BadRequestException("File too large");
        }

        Trip trip = p.getTrip();
        String ext = DocumentUploadSupport.extractExtension(upload.fileName());
        String s3Key = "trips/" + trip.id + "/passengers/" + p.id + "/" + UUID.randomUUID() + ext + ".enc";
        byte[] encrypted = documentCryptoService.encrypt(fileBytes);
        objectStorageService.putObject(s3Key, encrypted, "application/octet-stream");

        OperationalDocumentKind docKind = OperationalDocumentKind.fromString(kind);
        if (kind != null && !kind.isBlank()) {
            String upper = kind.trim().toUpperCase(Locale.ROOT);
            if ("PASSPORT".equals(upper) || "RG".equals(upper) || "CNH".equals(upper)) {
                docKind = OperationalDocumentKind.valueOf(upper);
            }
        }

        String docTitle = title != null && !title.isBlank() ? title.trim() : upload.fileName();
        TripDocument doc = TripDocument.builder()
                .trip(trip)
                .passenger(p)
                .title(docTitle.length() > 255 ? docTitle.substring(0, 255) : docTitle)
                .s3Key(s3Key)
                .contentType(upload.contentType())
                .sizeBytes((long) fileBytes.length)
                .encryptionVersion(documentCryptoService.currentVersion())
                .status(DocumentStatus.READY)
                .visibility(DocumentVisibility.INTERNAL)
                .documentKind(docKind)
                .docReviewStatus(PassengerDocReviewStatus.UPLOADED)
                .build();
        tripDocumentRepository.persist(doc);

        if (p.getDocumentType() == null && kind != null) {
            p.setDocumentType(kind.trim().toUpperCase(Locale.ROOT));
        }
        if (p.getFormStatus() == PassengerFormStatus.NOT_REQUESTED
                || p.getFormStatus() == PassengerFormStatus.INVITED) {
            p.setFormStatus(PassengerFormStatus.IN_PROGRESS);
        }
        passengerRepository.persist(p);

        auditService.record(
                trip, null, B2bTripLogAction.DOCUMENT_UPLOADED, "DOCUMENT", doc.id,
                "Documento de identidade enviado pelo passageiro " + label(p));
        return doc;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Trip requireTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId);
        if (trip == null) {
            throw new NotFoundException("Trip not found");
        }
        return trip;
    }

    private TripPassenger requirePassenger(UUID tripId, UUID passengerId) {
        return passengerRepository.findByIdAndTripId(passengerId, tripId)
                .orElseThrow(() -> new NotFoundException("Passenger not found"));
    }

    private TripPassenger requireByToken(String token) {
        return passengerRepository.findByInviteToken(token)
                .orElseThrow(() -> new NotFoundException("Formulário não encontrado"));
    }

    private boolean isLocked(TripPassenger p) {
        // Correção solicitada reabre o formulário para os campos apontados
        if (p.getFormStatus() == PassengerFormStatus.CORRECTION_REQUESTED) {
            return false;
        }
        return p.getFormStatus() == PassengerFormStatus.SUBMITTED
                || p.getFormStatus() == PassengerFormStatus.IN_REVIEW
                || p.getFormStatus() == PassengerFormStatus.COMPLETE;
    }

    private void clearOtherPrimaryContacts(UUID tripId, UUID exceptId) {
        for (TripPassenger other : passengerRepository.findByTripId(tripId)) {
            if (exceptId != null && other.id.equals(exceptId)) {
                continue;
            }
            if (other.isPrimaryContact()) {
                other.setPrimaryContact(false);
                passengerRepository.persist(other);
            }
        }
    }

    private TripPassengerResponse toAgentResponse(TripPassenger p) {
        var latestDoc = tripDocumentRepository.findLatestByPassengerId(p.id);
        PassengerDocReviewStatus docStatus = latestDoc
                .map(TripDocument::getDocReviewStatus)
                .orElse(p.getDocumentNumber() != null
                        ? PassengerDocReviewStatus.UPLOADED
                        : PassengerDocReviewStatus.NOT_PROVIDED);

        List<String> pending = new ArrayList<>();
        if (p.getDisplayName() == null || p.getDisplayName().isBlank()) {
            pending.add("Nome");
        }
        if (p.getFormStatus() == PassengerFormStatus.NOT_REQUESTED
                || p.getFormStatus() == PassengerFormStatus.INVITED) {
            pending.add("Formulário");
        } else if (p.getFormStatus() == PassengerFormStatus.IN_PROGRESS) {
            pending.add("Formulário incompleto");
        }
        if (docStatus == PassengerDocReviewStatus.NOT_PROVIDED) {
            pending.add("Documento");
        }
        if (p.getDocumentExpiresAt() != null && p.getDocumentExpiresAt().isBefore(LocalDate.now())) {
            pending.add("Documento vencido");
            docStatus = PassengerDocReviewStatus.EXPIRED;
        } else if (p.getDocumentExpiresAt() != null
                && p.getDocumentExpiresAt().isBefore(LocalDate.now().plusMonths(6))) {
            if (docStatus == PassengerDocReviewStatus.UPLOADED
                    || docStatus == PassengerDocReviewStatus.VALID) {
                docStatus = PassengerDocReviewStatus.EXPIRING;
                pending.add("Documento próximo do vencimento");
            }
        }

        return TripPassengerResponse.builder()
                .id(p.id)
                .tripId(p.getTrip() != null ? p.getTrip().id : null)
                .displayName(p.getDisplayName())
                .passengerType(p.getPassengerType())
                .formStatus(p.getFormStatus())
                .primaryContact(p.isPrimaryContact())
                .guardianPassengerId(p.getGuardian() != null ? p.getGuardian().id : null)
                .sourceClientId(p.getSourceClient() != null ? p.getSourceClient().id : null)
                .email(p.getEmail())
                .whatsapp(p.getWhatsapp())
                .phone(p.getPhone())
                .nationality(p.getNationality())
                .birthDate(p.getBirthDate())
                .documentType(p.getDocumentType())
                .documentNumberMasked(maskDocument(p.getDocumentNumber()))
                .documentExpiresAt(p.getDocumentExpiresAt())
                .sortOrder(p.getSortOrder())
                .inviteToken(p.getInviteToken())
                .inviteUrl(p.getInviteToken() != null ? formUrl(p.getInviteToken()) : null)
                .inviteSentAt(p.getInviteSentAt() != null ? p.getInviteSentAt().toString() : null)
                .submittedAt(p.getSubmittedAt() != null ? p.getSubmittedAt().toString() : null)
                .formPayload(maskPayloadForAgent(p.getFormPayload()))
                .docReviewStatus(docStatus)
                .latestDocumentId(latestDoc.map(d -> d.id).orElse(null))
                .latestDocumentKind(latestDoc.map(d -> d.getDocumentKind() != null
                        ? d.getDocumentKind().name() : null).orElse(p.getDocumentType()))
                .pendingItems(pending)
                .openCorrections(correctionRepository.findOpenByPassengerId(p.id).stream()
                        .map(this::toCorrectionResponse)
                        .toList())
                .hasTravelerProfileConsent(
                        p.getSourceClient() != null
                                && p.getSourceClient().getTravelerReuseConsentAt() != null)
                .createdAt(p.getCreatedAt() != null ? p.getCreatedAt().toString() : null)
                .updatedAt(p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : null)
                .build();
    }

    private PublicPassengerFormResponse toPublicResponse(TripPassenger p) {
        Trip trip = p.getTrip();
        AgencyOpportunity opp = opportunityRepository.findByTripId(trip.id).orElse(null);
        boolean hasFlights = (trip.getFlightDetails() != null && !trip.getFlightDetails().isBlank())
                || (opp != null && Boolean.TRUE.equals(opp.getBudgetIncludesFlights()));
        boolean hasHotels = trip.getHotelDetails() != null && !trip.getHotelDetails().isBlank();
        boolean isInternational = resolveInternational(opp);
        AgencyClient reusable = findConsentedClientForPassenger(p);

        return PublicPassengerFormResponse.builder()
                .passengerId(p.id)
                .tripId(trip.id)
                .tripName(trip.getName())
                .displayName(p.getDisplayName())
                .passengerType(p.getPassengerType())
                .formStatus(p.getFormStatus())
                .primaryContact(p.isPrimaryContact())
                .guardianPassengerId(p.getGuardian() != null ? p.getGuardian().id : null)
                .email(p.getEmail())
                .whatsapp(p.getWhatsapp())
                .nationality(p.getNationality())
                .birthDate(p.getBirthDate())
                .documentType(p.getDocumentType())
                .documentNumber(p.getDocumentNumber())
                .documentExpiresAt(p.getDocumentExpiresAt())
                .formPayload(p.getFormPayload())
                .isInternational(isInternational)
                .hasFlights(hasFlights)
                .hasHotels(hasHotels)
                .readOnly(isLocked(p))
                .openCorrections(correctionRepository.findOpenByPassengerId(p.id).stream()
                        .map(this::toCorrectionResponse)
                        .toList())
                .reusableProfileAvailable(reusable != null)
                .reusableProfileName(reusable != null ? reusable.getName() : null)
                .build();
    }

    private boolean resolveInternational(AgencyOpportunity opp) {
        if (opp == null) {
            return true;
        }
        String tripType = opp.getTripType();
        if (tripType != null) {
            String t = tripType.toLowerCase(Locale.ROOT);
            if (t.contains("nacional") || t.contains("domestic")) {
                return false;
            }
            if (t.contains("internacional") || t.contains("international")) {
                return true;
            }
        }
        String dest = opp.getDestinations();
        if (dest != null) {
            String d = dest.toLowerCase(Locale.ROOT);
            boolean mentionsBrazil = d.contains("brasil") || d.contains("brazil");
            boolean mentionsForeign =
                    d.contains("portugal") || d.contains("eua") || d.contains("europa")
                            || d.contains("argentina") || d.contains("chile") || d.contains("uruguai")
                            || d.contains("mexico") || d.contains("méxico") || d.contains("espanha")
                            || d.contains("itália") || d.contains("italia") || d.contains("frança");
            if (mentionsForeign) {
                return true;
            }
            if (mentionsBrazil && !mentionsForeign) {
                return false;
            }
        }
        return true;
    }

    private String mergePayload(String existing, String patch) {
        if (patch == null || patch.isBlank()) {
            return existing;
        }
        if (existing == null || existing.isBlank()) {
            return patch;
        }
        try {
            JsonNode base = objectMapper.readTree(existing);
            JsonNode overlay = objectMapper.readTree(patch);
            if (!base.isObject() || !overlay.isObject()) {
                return patch;
            }
            ObjectNode merged = ((ObjectNode) base).deepCopy();
            overlay.fields().forEachRemaining(e -> merged.set(e.getKey(), e.getValue()));
            return objectMapper.writeValueAsString(merged);
        } catch (Exception e) {
            return patch;
        }
    }

    private String maskPayloadForAgent(String payload) {
        if (payload == null || payload.isBlank()) {
            return payload;
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (!node.isObject()) {
                return payload;
            }
            ObjectNode copy = ((ObjectNode) node).deepCopy();
            maskField(copy, "documentNumber");
            maskField(copy, "document_number");
            if (copy.has("documentation") && copy.get("documentation").isObject()) {
                ObjectNode doc = (ObjectNode) copy.get("documentation");
                maskField(doc, "number");
                maskField(doc, "documentNumber");
            }
            return objectMapper.writeValueAsString(copy);
        } catch (Exception e) {
            return payload;
        }
    }

    private void maskField(ObjectNode node, String field) {
        if (node.has(field) && node.get(field).isTextual()) {
            node.put(field, maskDocument(node.get(field).asText()));
        }
    }

    static String maskDocument(String number) {
        if (number == null || number.isBlank()) {
            return null;
        }
        String n = number.trim();
        if (n.length() <= 4) {
            return "••••";
        }
        return n.charAt(0) + "•••••" + n.substring(n.length() - 2);
    }

    private String generateUniqueToken() {
        for (int i = 0; i < 8; i++) {
            String token = randomToken(40);
            if (passengerRepository.findByInviteToken(token).isEmpty()) {
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

    private String formUrl(String token) {
        String base = publicUrl == null ? "http://localhost:3000" : publicUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/passenger-form/" + token;
    }

    private static String label(TripPassenger p) {
        if (p.getDisplayName() != null && !p.getDisplayName().isBlank()) {
            return p.getDisplayName();
        }
        return p.getPassengerType() != null ? p.getPassengerType().name() : "passageiro";
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String escapeHtml(String s) {
        return s == null
                ? ""
                : s.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;");
    }

    private PassengerCorrectionResponse toCorrectionResponse(PassengerFieldCorrection c) {
        return PassengerCorrectionResponse.builder()
                .id(c.id)
                .passengerId(c.getPassenger() != null ? c.getPassenger().id : null)
                .fieldName(c.getFieldName())
                .oldValue(c.getOldValue())
                .expectedValue(c.getExpectedValue())
                .correctedValue(c.getCorrectedValue())
                .agentNote(c.getAgentNote())
                .status(c.getStatus())
                .requestedAt(c.getRequestedAt() != null ? c.getRequestedAt().toString() : null)
                .resolvedAt(c.getResolvedAt() != null ? c.getResolvedAt().toString() : null)
                .build();
    }

    private String readFieldValue(TripPassenger p, String field) {
        return switch (field) {
            case "displayName" -> p.getDisplayName();
            case "email" -> p.getEmail();
            case "whatsapp" -> p.getWhatsapp();
            case "phone" -> p.getPhone();
            case "nationality" -> p.getNationality();
            case "birthDate" -> p.getBirthDate() != null ? p.getBirthDate().toString() : null;
            case "documentType" -> p.getDocumentType();
            case "documentNumber" -> p.getDocumentNumber();
            case "documentExpiresAt" ->
                    p.getDocumentExpiresAt() != null ? p.getDocumentExpiresAt().toString() : null;
            default -> null;
        };
    }

    private void applyFieldValue(TripPassenger p, String field, String value) {
        switch (field) {
            case "displayName" -> p.setDisplayName(value);
            case "email" -> p.setEmail(value);
            case "whatsapp" -> p.setWhatsapp(value);
            case "phone" -> p.setPhone(value);
            case "nationality" -> p.setNationality(value);
            case "birthDate" -> {
                try {
                    p.setBirthDate(LocalDate.parse(value));
                } catch (Exception e) {
                    throw new BadRequestException("Invalid birthDate");
                }
            }
            case "documentType" -> p.setDocumentType(value);
            case "documentNumber" -> p.setDocumentNumber(value);
            case "documentExpiresAt" -> {
                try {
                    p.setDocumentExpiresAt(LocalDate.parse(value));
                } catch (Exception e) {
                    throw new BadRequestException("Invalid documentExpiresAt");
                }
            }
            default -> throw new BadRequestException("Unsupported field: " + field);
        }
    }

    private AgencyClient findConsentedClientForPassenger(TripPassenger p) {
        if (p.getSourceClient() != null
                && p.getSourceClient().getTravelerReuseConsentAt() != null) {
            return p.getSourceClient();
        }
        Trip trip = p.getTrip();
        if (trip == null || trip.getAgency() == null) {
            return null;
        }
        if (trip.getClient() != null && trip.getClient().getTravelerReuseConsentAt() != null) {
            return trip.getClient();
        }
        if (p.getEmail() != null && !p.getEmail().isBlank()) {
            AgencyClient byEmail = agencyClientRepository
                    .find(
                            "agency.id = ?1 AND lower(email) = ?2 AND travelerReuseConsentAt IS NOT NULL",
                            trip.getAgency().id,
                            p.getEmail().trim().toLowerCase(Locale.ROOT))
                    .firstResult();
            if (byEmail != null) {
                return byEmail;
            }
        }
        return null;
    }

    private static String escJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
