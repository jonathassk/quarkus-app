package org.example.application.services.ops;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.ops.AddOperationalDeadlineRequest;
import org.example.application.dto.ops.AssignSupplierRequest;
import org.example.application.dto.ops.CancelOperationalServiceRequest;
import org.example.application.dto.ops.ConfirmOperationalServiceRequest;
import org.example.application.dto.ops.CreateServiceChangeRequest;
import org.example.application.dto.ops.LinkPassengersRequest;
import org.example.application.dto.ops.OperationalDeadlineDTO;
import org.example.application.dto.ops.OperationalDocumentDTO;
import org.example.application.dto.ops.OperationalServiceDTO;
import org.example.application.dto.ops.PublishedTripItineraryDTO;
import org.example.application.dto.ops.ServiceChangeRequestDTO;
import org.example.application.dto.ops.TripOperationsWorkspaceDTO;
import org.example.application.dto.ops.UpdateOperationalDocumentStatusRequest;
import org.example.application.dto.ops.UpdateOperationalServiceStatusRequest;
import org.example.application.dto.ops.UpdateServiceChangeRequest;
import org.example.application.dto.passenger.TripPassengerResponse;
import org.example.application.services.B2bAuditService;
import org.example.application.services.passenger.TripPassengerService;
import org.example.domain.entity.AgencyMember;
import org.example.domain.entity.AgencySupplier;
import org.example.domain.entity.OperationalDeadline;
import org.example.domain.entity.OperationalService;
import org.example.domain.entity.ProposalAcceptance;
import org.example.domain.entity.ProposalItem;
import org.example.domain.entity.ProposalOption;
import org.example.domain.entity.ProposalVersion;
import org.example.domain.entity.ServiceChangeRequest;
import org.example.domain.entity.Trip;
import org.example.domain.entity.TripDocument;
import org.example.domain.entity.TripPassenger;
import org.example.domain.enums.AgencyRole;
import org.example.domain.enums.B2bTripLogAction;
import org.example.domain.enums.DocumentVisibility;
import org.example.domain.enums.OperationalAlertLevel;
import org.example.domain.enums.OperationalDeadlineType;
import org.example.domain.enums.OperationalDocumentKind;
import org.example.domain.enums.OperationalDocumentStatus;
import org.example.domain.enums.OperationalNextAction;
import org.example.domain.enums.OperationalServiceStatus;
import org.example.domain.enums.OperationalServiceType;
import org.example.domain.enums.OperationStatus;
import org.example.domain.enums.PassengerFormStatus;
import org.example.domain.enums.ProposalItemType;
import org.example.domain.enums.ProposalStatus;
import org.example.domain.enums.ServiceChangeStatus;
import org.example.domain.enums.SupplierCategory;
import org.example.domain.repository.AgencyMemberRepository;
import org.example.domain.repository.AgencySupplierRepository;
import org.example.domain.repository.OperationalDeadlineRepository;
import org.example.domain.repository.OperationalServiceRepository;
import org.example.domain.repository.ProposalAcceptanceRepository;
import org.example.domain.repository.ProposalItemRepository;
import org.example.domain.repository.ProposalOptionRepository;
import org.example.domain.repository.ServiceChangeRequestRepository;
import org.example.domain.repository.TripDocumentRepository;
import org.example.domain.repository.TripPassengerRepository;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Workspace de operação e reservas (MVP §8). MARKER:OPS_FULL_V1 */
@Slf4j
@ApplicationScoped
public class OperationalWorkspaceService {

    private static final Set<OperationalDocumentKind> VOUCHER_KINDS = Set.of(
            OperationalDocumentKind.VOUCHER,
            OperationalDocumentKind.TICKET,
            OperationalDocumentKind.HOTEL_CONFIRMATION,
            OperationalDocumentKind.POLICY);

    @Inject TripRepository tripRepository;
    @Inject AgencyMemberRepository agencyMemberRepository;
    @Inject ProposalAcceptanceRepository acceptanceRepository;
    @Inject ProposalOptionRepository optionRepository;
    @Inject ProposalItemRepository itemRepository;
    @Inject OperationalServiceRepository serviceRepository;
    @Inject OperationalDeadlineRepository deadlineRepository;
    @Inject AgencySupplierRepository supplierRepository;
    @Inject TripDocumentRepository documentRepository;
    @Inject ServiceChangeRequestRepository changeRequestRepository;
    @Inject TripPassengerRepository tripPassengerRepository;
    @Inject UserRepository userRepository;
    @Inject TripPassengerService passengerService;
    @Inject OperationStatusRollup statusRollup;
    @Inject B2bAuditService auditService;

    @Transactional
    public int materializeFromApprovedProposal(Trip trip) {
        if (trip == null || trip.getAgency() == null) {
            return 0;
        }
        passengerService.seedFromOpportunityQuiet(trip.id);

        ProposalOption option = resolveApprovedOption(trip);
        if (option == null) {
            log.debug("No approved option for trip {} — skip ops materialization", trip.id);
            if (trip.getOperationStatus() == null) {
                trip.setOperationStatus(OperationStatus.PREPARING_RESERVATIONS);
                tripRepository.persist(trip);
            }
            return 0;
        }

        ProposalVersion version = option.getVersion();
        List<ProposalItem> items = itemRepository.findForOptionPricing(version.id, option.id);
        int created = 0;
        int sort = (int) serviceRepository.countByTripId(trip.id);
        for (ProposalItem item : items) {
            if (item.getItemType() == ProposalItemType.PACKAGE && items.size() > 1) {
                continue;
            }
            if (serviceRepository.findByProposalItemId(item.id).isPresent()) {
                continue;
            }
            OperationalService svc = buildFromItem(trip, item, sort++);
            serviceRepository.persist(svc);
            if (item.getQuoteExpiresAt() != null) {
                deadlineRepository.persist(OperationalDeadline.builder()
                        .service(svc)
                        .trip(trip)
                        .deadlineType(OperationalDeadlineType.QUOTE_VALIDITY)
                        .title("Validade da cotação — " + item.getName())
                        .dueAt(item.getQuoteExpiresAt())
                        .alertLevel(OperationalAlertLevel.WARNING)
                        .build());
            }
            created++;
        }

        if (trip.getOperationStatus() == null) {
            trip.setOperationStatus(OperationStatus.PREPARING_RESERVATIONS);
        }
        refreshTripOperationStatus(trip);
        if (created > 0) {
            auditService.recordExternalActor(trip, "Sistema",
                    B2bTripLogAction.OPS_SERVICES_MATERIALIZED,
                    "TRIP", trip.id, null, "{\"created\":" + created + "}",
                    "Serviços operacionais gerados a partir da proposta", null);
            log.info("Materialized {} operational services for trip {}", created, trip.id);
        }
        return created;
    }

    @Transactional
    public void materializeForTripId(UUID tripId) {
        Trip trip = tripRepository.findById(tripId);
        if (trip != null) {
            materializeFromApprovedProposal(trip);
        }
    }

    @Transactional
    public TripOperationsWorkspaceDTO getWorkspace(UUID tripId, UUID userId) {
        Trip trip = requireAgencyTripAccess(tripId, userId);
        if (isOpsEligible(trip) && serviceRepository.countByTripId(tripId) == 0) {
            materializeFromApprovedProposal(trip);
            trip = tripRepository.findById(tripId);
        }

        List<OperationalService> services = serviceRepository.findByTripId(tripId);
        List<OperationalDeadline> deadlines = deadlineRepository.findByTripId(tripId);
        List<TripPassengerResponse> passengers = passengerService.list(tripId);
        List<TripDocument> operationalDocs = documentRepository.findOperationalByTripId(tripId);
        List<ServiceChangeRequest> changes = changeRequestRepository.findByTripId(tripId);
        Map<UUID, List<TripDocument>> docsByService = operationalDocs.stream()
                .filter(d -> d.getOperationalService() != null)
                .collect(Collectors.groupingBy(d -> d.getOperationalService().id));
        refreshTripOperationStatus(trip);

        ReadinessResult readiness = computeReadiness(services, passengers, docsByService);
        return TripOperationsWorkspaceDTO.builder()
                .tripId(trip.id)
                .tripName(trip.getName())
                .clientName(trip.getClient() != null ? trip.getClient().getName() : trip.getProposalClientName())
                .destination(trip.getName())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .daysUntilDeparture(trip.getStartDate() != null
                        ? (int) ChronoUnit.DAYS.between(LocalDate.now(), trip.getStartDate()) : null)
                .consultantId(trip.getAssignedConsultant() != null ? trip.getAssignedConsultant().id : null)
                .consultantName(trip.getAssignedConsultant() != null ? trip.getAssignedConsultant().getFullName() : null)
                .proposalStatus(trip.getProposalStatus())
                .operationStatus(trip.getOperationStatus())
                .readinessPercent(readiness.percent())
                .readinessChecks(readiness.checks())
                .alerts(buildAlerts(trip, services, deadlines))
                .services(services.stream().map(s -> toServiceDto(s, docsByService.getOrDefault(s.id, List.of()))).toList())
                .passengers(passengers)
                .deadlines(deadlines.stream().map(this::toDeadlineDto).toList())
                .pendencies(buildPendencies(services, passengers))
                .documents(operationalDocs.stream().map(this::toDocumentDto).toList())
                .changeRequests(changes.stream().map(this::toChangeDto).toList())
                .build();
    }

    @Transactional
    public OperationalServiceDTO updateStatus(
            UUID tripId, UUID serviceId, UUID userId, UpdateOperationalServiceStatusRequest request) {
        Trip trip = requireAgencyTripAccess(tripId, userId);
        OperationalService svc = requireService(trip, serviceId);
        if (request == null) throw new BadRequestException("request is required");
        if (request.getStatus() != null) {
            if (request.getStatus() == OperationalServiceStatus.ISSUED && !canIssue(svc.getServiceType())) {
                throw new BadRequestException("Status EMITIDO só se aplica a voo e seguro");
            }
            svc.setStatus(request.getStatus());
            if (request.getStatus().isTerminalSuccess() && svc.getConfirmedAt() == null) {
                svc.setConfirmedAt(Instant.now());
            }
            if (request.getStatus() == OperationalServiceStatus.CANCELLED) {
                svc.setCancelledAt(Instant.now());
                svc.setCancelledBy(userRepository.findById(userId));
                svc.setNextAction(OperationalNextAction.NONE);
                svc.setPublished(false);
            }
        }
        if (request.getNextAction() != null) {
            svc.setNextAction(request.getNextAction());
            if (request.getNextActionLabel() == null) {
                svc.setNextActionLabel(request.getNextAction().defaultLabelPt());
            }
        }
        if (request.getNextActionLabel() != null) svc.setNextActionLabel(request.getNextActionLabel());
        if (request.getNextActionDueAt() != null) svc.setNextActionDueAt(request.getNextActionDueAt());
        if (request.getInternalNotes() != null) svc.setInternalNotes(request.getInternalNotes());
        if (request.getDetails() != null) svc.setDetails(request.getDetails());
        if (request.getPublicInfo() != null) svc.setPublicInfo(request.getPublicInfo());
        serviceRepository.persist(svc);
        refreshTripOperationStatus(trip);
        auditService.record(trip, userId, B2bTripLogAction.OPS_SERVICE_STATUS_CHANGED,
                "OPS_SERVICE", svc.id, null, "{\"status\":\"" + svc.getStatus() + "\"}",
                "Status operacional: " + svc.getName() + " → " + svc.getStatus(), null);
        return toServiceDto(svc);
    }

    @Transactional
    public OperationalServiceDTO confirm(
            UUID tripId, UUID serviceId, UUID userId, ConfirmOperationalServiceRequest request) {
        Trip trip = requireAgencyTripAccess(tripId, userId);
        OperationalService svc = requireService(trip, serviceId);
        if (request == null) throw new BadRequestException("request is required");
        Long confirmed = request.getConfirmedCostMinor();
        if (confirmed != null && svc.getCostEstimatedMinor() != null
                && !confirmed.equals(svc.getCostEstimatedMinor())
                && !Boolean.TRUE.equals(request.getAcceptCostDivergence())) {
            throw new BadRequestException(
                    "O valor confirmado difere do custo estimado. Envie acceptCostDivergence=true.");
        }
        boolean preferIssued = request.getMarkIssued() == null
                ? canIssue(svc.getServiceType())
                : Boolean.TRUE.equals(request.getMarkIssued());
        svc.setStatus(preferIssued && canIssue(svc.getServiceType())
                ? OperationalServiceStatus.ISSUED : OperationalServiceStatus.CONFIRMED);
        svc.setLocator(blankToNull(request.getLocator()));
        if (request.getTicketNumber() != null) svc.setTicketNumber(blankToNull(request.getTicketNumber()));
        if (confirmed != null) {
            svc.setConfirmedCostMinor(confirmed);
            svc.setCostDivergenceMinor(svc.getCostEstimatedMinor() != null
                    ? confirmed - svc.getCostEstimatedMinor() : 0L);
        }
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            svc.setCurrency(request.getCurrency().trim().toUpperCase(Locale.ROOT));
        }
        if (request.getCancellationPolicy() != null) svc.setCancellationPolicy(request.getCancellationPolicy());
        if (request.getPublicInfo() != null) svc.setPublicInfo(request.getPublicInfo());
        if (request.getInternalNotes() != null) svc.setInternalNotes(request.getInternalNotes());
        svc.setConfirmedAt(Instant.now());
        svc.setNextAction(OperationalNextAction.REQUEST_VOUCHER);
        svc.setNextActionLabel(OperationalNextAction.REQUEST_VOUCHER.defaultLabelPt());
        if (Boolean.TRUE.equals(request.getPublish())) svc.setPublished(true);
        serviceRepository.persist(svc);
        refreshTripOperationStatus(trip);
        auditService.record(trip, userId, B2bTripLogAction.OPS_SERVICE_CONFIRMED,
                "OPS_SERVICE", svc.id, null,
                "{\"locator\":\"" + (svc.getLocator() != null ? svc.getLocator() : "") + "\"}",
                "Confirmação: " + svc.getName(), null);
        return toServiceDto(svc);
    }

    @Transactional
    public OperationalServiceDTO cancel(
            UUID tripId, UUID serviceId, UUID userId, CancelOperationalServiceRequest request) {
        Trip trip = requireAgencyTripAccess(tripId, userId);
        OperationalService svc = requireService(trip, serviceId);
        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            throw new BadRequestException("reason is required");
        }
        svc.setStatus(OperationalServiceStatus.CANCELLED);
        svc.setCancelReason(request.getReason().trim());
        if (request.getCancellationPolicy() != null) svc.setCancellationPolicy(request.getCancellationPolicy());
        if (request.getEstimatedPenaltyMinor() != null) svc.setEstimatedPenaltyMinor(request.getEstimatedPenaltyMinor());
        if (request.getSupplierCreditMinor() != null) svc.setSupplierCreditMinor(request.getSupplierCreditMinor());
        svc.setCancelledAt(Instant.now());
        svc.setCancelledBy(userRepository.findById(userId));
        svc.setNextAction(OperationalNextAction.NONE);
        svc.setPublished(false);
        serviceRepository.persist(svc);
        refreshTripOperationStatus(trip);
        auditService.record(trip, userId, B2bTripLogAction.OPS_SERVICE_CANCELLED,
                "OPS_SERVICE", svc.id, null, null, "Cancelamento operacional: " + svc.getName(), null);
        return toServiceDto(svc);
    }

    @Transactional
    public OperationalServiceDTO setPublished(UUID tripId, UUID serviceId, UUID userId, boolean published) {
        Trip trip = requireAgencyTripAccess(tripId, userId);
        OperationalService svc = requireService(trip, serviceId);
        if (published && !svc.getStatus().isTerminalSuccess()) {
            throw new BadRequestException("Somente serviços confirmados/emitidos podem ser publicados");
        }
        svc.setPublished(published);
        serviceRepository.persist(svc);
        auditService.record(trip, userId, B2bTripLogAction.OPS_SERVICE_PUBLISHED,
                "OPS_SERVICE", svc.id, null, "{\"published\":" + published + "}",
                (published ? "Publicado" : "Ocultado") + ": " + svc.getName(), null);
        return toServiceDto(svc);
    }

    @Transactional
    public OperationalDeadlineDTO addDeadline(
            UUID tripId, UUID serviceId, UUID userId, AddOperationalDeadlineRequest request) {
        Trip trip = requireAgencyTripAccess(tripId, userId);
        OperationalService svc = requireService(trip, serviceId);
        if (request == null || request.getDueAt() == null || request.getDeadlineType() == null) {
            throw new BadRequestException("deadlineType and dueAt are required");
        }
        String title = request.getTitle() != null && !request.getTitle().isBlank()
                ? request.getTitle().trim() : request.getDeadlineType().name();
        OperationalDeadline dl = OperationalDeadline.builder()
                .service(svc).trip(trip)
                .deadlineType(request.getDeadlineType())
                .title(title)
                .dueAt(request.getDueAt())
                .alertLevel(request.getAlertLevel() != null ? request.getAlertLevel() : OperationalAlertLevel.INFO)
                .build();
        deadlineRepository.persist(dl);
        if (svc.getNextActionDueAt() == null || request.getDueAt().isBefore(svc.getNextActionDueAt())) {
            svc.setNextActionDueAt(request.getDueAt());
            serviceRepository.persist(svc);
        }
        return toDeadlineDto(dl);
    }

    @Transactional
    public OperationalDeadlineDTO completeDeadline(UUID tripId, UUID deadlineId, UUID userId) {
        Trip trip = requireAgencyTripAccess(tripId, userId);
        OperationalDeadline dl = deadlineRepository.findById(deadlineId);
        if (dl == null || !dl.getTrip().id.equals(trip.id)) {
            throw new NotFoundException("Deadline not found");
        }
        dl.setCompletedAt(Instant.now());
        deadlineRepository.persist(dl);
        auditService.record(trip, userId, B2bTripLogAction.OPS_DEADLINE_COMPLETED,
                "OPS_DEADLINE", dl.id, null, null,
                "Prazo concluído: " + dl.getTitle(), null);
        return toDeadlineDto(dl);
    }

    @Transactional
    public OperationalServiceDTO linkPassengers(
            UUID tripId, UUID serviceId, UUID userId, LinkPassengersRequest request) {
        Trip trip = requireAgencyTripAccess(tripId, userId);
        OperationalService svc = requireService(trip, serviceId);
        if (request == null) throw new BadRequestException("request is required");
        Set<TripPassenger> linked = new HashSet<>();
        List<UUID> ids = request.getPassengerIds() != null ? request.getPassengerIds() : List.of();
        for (UUID passengerId : ids) {
            TripPassenger p = tripPassengerRepository.findByIdAndTripId(passengerId, tripId)
                    .orElseThrow(() -> new BadRequestException("Passenger not found on trip: " + passengerId));
            linked.add(p);
        }
        svc.setPassengers(linked);
        serviceRepository.persist(svc);
        auditService.record(trip, userId, B2bTripLogAction.OPS_PASSENGERS_LINKED,
                "OPS_SERVICE", svc.id, null, "{\"count\":" + linked.size() + "}",
                "Passageiros vinculados: " + svc.getName(), null);
        return toServiceDto(svc);
    }

    @Transactional
    public ServiceChangeRequestDTO createChangeRequest(
            UUID tripId, UUID serviceId, UUID userId, CreateServiceChangeRequest request) {
        Trip trip = requireAgencyTripAccess(tripId, userId);
        OperationalService svc = requireService(trip, serviceId);
        if (request == null) throw new BadRequestException("request is required");
        ServiceChangeRequest change = ServiceChangeRequest.builder()
                .trip(trip)
                .service(svc)
                .status(ServiceChangeStatus.REQUESTED)
                .requestNote(blankToNull(request.getRequestNote()))
                .priceDeltaMinor(request.getPriceDeltaMinor())
                .requestedBy(userRepository.findById(userId))
                .build();
        changeRequestRepository.persist(change);
        svc.setStatus(OperationalServiceStatus.CHANGE_PENDING);
        serviceRepository.persist(svc);
        refreshTripOperationStatus(trip);
        auditService.record(trip, userId, B2bTripLogAction.OPS_CHANGE_REQUESTED,
                "OPS_CHANGE", change.id, null, null,
                "Alteração solicitada: " + svc.getName(), null);
        return toChangeDto(change);
    }

    @Transactional
    public ServiceChangeRequestDTO updateChangeRequest(
            UUID tripId, UUID changeId, UUID userId, UpdateServiceChangeRequest request) {
        Trip trip = requireAgencyTripAccess(tripId, userId);
        ServiceChangeRequest change = changeRequestRepository.findById(changeId);
        if (change == null || !change.getTrip().id.equals(trip.id)) {
            throw new NotFoundException("Change request not found");
        }
        if (request == null) throw new BadRequestException("request is required");
        if (request.getRequestNote() != null) {
            change.setRequestNote(blankToNull(request.getRequestNote()));
        }
        if (request.getPriceDeltaMinor() != null) {
            change.setPriceDeltaMinor(request.getPriceDeltaMinor());
        }
        if (request.getStatus() != null) {
            change.setStatus(request.getStatus());
            if (request.getStatus() == ServiceChangeStatus.EXECUTED
                    || request.getStatus() == ServiceChangeStatus.REFUSED
                    || request.getStatus() == ServiceChangeStatus.CANCELLED) {
                change.setResolvedAt(Instant.now());
            }
            OperationalService svc = change.getService();
            if ((request.getStatus() == ServiceChangeStatus.APPROVED
                    || request.getStatus() == ServiceChangeStatus.EXECUTED)
                    && svc.getStatus() == OperationalServiceStatus.CHANGE_PENDING) {
                svc.setStatus(canIssue(svc.getServiceType())
                        ? OperationalServiceStatus.ISSUED
                        : OperationalServiceStatus.CONFIRMED);
                serviceRepository.persist(svc);
                refreshTripOperationStatus(trip);
            }
        }
        changeRequestRepository.persist(change);
        auditService.record(trip, userId, B2bTripLogAction.OPS_CHANGE_UPDATED,
                "OPS_CHANGE", change.id, null,
                "{\"status\":\"" + change.getStatus() + "\"}",
                "Alteração atualizada: " + change.getService().getName(), null);
        return toChangeDto(change);
    }

    @Transactional
    public OperationalDocumentDTO updateDocumentStatus(
            UUID tripId, UUID documentId, UUID userId, UpdateOperationalDocumentStatusRequest request) {
        Trip trip = requireAgencyTripAccess(tripId, userId);
        TripDocument doc = documentRepository.findByIdAndTripId(documentId, tripId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        if (request == null || request.getStatus() == null) {
            throw new BadRequestException("status is required");
        }
        doc.setOperationalDocStatus(request.getStatus());
        documentRepository.persist(doc);
        if (request.getStatus() == OperationalDocumentStatus.SENT_TO_CLIENT
                && doc.getOperationalService() != null
                && doc.getOperationalService().getNextAction() == OperationalNextAction.REQUEST_VOUCHER) {
            OperationalService svc = doc.getOperationalService();
            clearVoucherNextAction(svc);
            serviceRepository.persist(svc);
        }
        auditService.record(trip, userId, B2bTripLogAction.OPS_DOCUMENT_STATUS_CHANGED,
                "DOCUMENT", doc.id, null,
                "{\"status\":\"" + request.getStatus() + "\"}",
                "Status do documento: " + doc.getTitle(), null);
        return toDocumentDto(doc);
    }

    @Transactional
    public OperationalDocumentDTO linkDocumentToService(
            UUID tripId, UUID documentId, UUID serviceId, UUID userId, OperationalDocumentKind documentKind) {
        Trip trip = requireAgencyTripAccess(tripId, userId);
        OperationalService svc = requireService(trip, serviceId);
        TripDocument doc = documentRepository.findByIdAndTripId(documentId, tripId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        OperationalDocumentKind kind = documentKind != null ? documentKind : OperationalDocumentKind.VOUCHER;
        doc.setOperationalService(svc);
        doc.setDocumentKind(kind);
        doc.setOperationalDocStatus(OperationalDocumentStatus.RECEIVED);
        documentRepository.persist(doc);
        if (VOUCHER_KINDS.contains(kind)
                && svc.getNextAction() == OperationalNextAction.REQUEST_VOUCHER) {
            clearVoucherNextAction(svc);
            serviceRepository.persist(svc);
        }
        auditService.record(trip, userId, B2bTripLogAction.OPS_DOCUMENT_LINKED,
                "DOCUMENT", doc.id, null,
                "{\"serviceId\":\"" + serviceId + "\",\"kind\":\"" + kind + "\"}",
                "Documento vinculado: " + doc.getTitle() + " → " + svc.getName(), null);
        return toDocumentDto(doc);
    }

    @Transactional
    public OperationalServiceDTO assignSupplier(
            UUID tripId, UUID serviceId, UUID userId, AssignSupplierRequest request) {
        Trip trip = requireAgencyTripAccess(tripId, userId);
        OperationalService svc = requireService(trip, serviceId);
        if (request == null) throw new BadRequestException("request is required");
        AgencySupplier supplier;
        if (request.getSupplierId() != null) {
            supplier = supplierRepository.findById(request.getSupplierId());
            if (supplier == null || !supplier.getAgency().id.equals(trip.getAgency().id)) {
                throw new NotFoundException("Supplier not found");
            }
        } else if (request.getSupplierName() != null && !request.getSupplierName().isBlank()) {
            String name = request.getSupplierName().trim();
            supplier = supplierRepository.findByAgencyAndNameIgnoreCase(trip.getAgency().id, name)
                    .orElseGet(() -> {
                        AgencySupplier created = AgencySupplier.builder()
                                .agency(trip.getAgency())
                                .name(name)
                                .category(SupplierCategory.fromServiceType(svc.getServiceType()))
                                .build();
                        supplierRepository.persist(created);
                        return created;
                    });
        } else {
            throw new BadRequestException("supplierId or supplierName is required");
        }
        svc.setSupplier(supplier);
        svc.setSupplierName(supplier.getName());
        serviceRepository.persist(svc);
        auditService.record(trip, userId, B2bTripLogAction.OPS_SUPPLIER_ASSIGNED,
                "OPS_SERVICE", svc.id, null,
                "{\"supplierId\":\"" + supplier.id + "\"}",
                "Fornecedor atribuído: " + supplier.getName() + " → " + svc.getName(), null);
        return toServiceDto(svc);
    }

    @Transactional
    public PublishedTripItineraryDTO getPublishedItinerary(UUID tripId, UUID userId) {
        Trip trip = requireTripMemberOrAgencyAccess(tripId, userId);
        List<OperationalService> services = serviceRepository.findByTripId(tripId).stream()
                .filter(s -> s.isPublished() && s.getStatus().isSettled())
                .toList();
        List<PublishedTripItineraryDTO.PublishedServiceDTO> published = new ArrayList<>();
        for (OperationalService svc : services) {
            List<OperationalDocumentDTO> docs = documentRepository.findByOperationalServiceId(svc.id).stream()
                    .filter(d -> d.getVisibility() == DocumentVisibility.CLIENT)
                    .map(this::toDocumentDto)
                    .toList();
            Map<String, Object> details = svc.getDetails();
            published.add(PublishedTripItineraryDTO.PublishedServiceDTO.builder()
                    .id(svc.id)
                    .serviceType(svc.getServiceType())
                    .name(svc.getName())
                    .subtitle(svc.getSubtitle())
                    .locator(svc.getLocator())
                    .ticketNumber(svc.getTicketNumber())
                    .publicInfo(svc.getPublicInfo())
                    .startDate(extractDate(details, "startDate", "checkIn", "date"))
                    .endDate(extractDate(details, "endDate", "checkOut"))
                    .documents(docs)
                    .build());
        }
        return PublishedTripItineraryDTO.builder()
                .tripId(trip.id)
                .tripName(trip.getName())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .services(published)
                .build();
    }

    private ProposalOption resolveApprovedOption(Trip trip) {
        for (ProposalAcceptance a : acceptanceRepository.findByTripId(trip.id)) {
            if (a.getOption() != null) return a.getOption();
        }
        return optionRepository.findByTripId(trip.id).orElse(null);
    }

    private OperationalService buildFromItem(Trip trip, ProposalItem item, int sortOrder) {
        OperationalServiceType type = OperationalServiceType.fromProposalItemType(
                item.getItemType() != null ? item.getItemType() : ProposalItemType.OTHER);
        AgencySupplier supplier = null;
        if (item.getSupplierName() != null && !item.getSupplierName().isBlank()) {
            supplier = supplierRepository.findByAgencyAndNameIgnoreCase(trip.getAgency().id, item.getSupplierName())
                    .orElseGet(() -> {
                        AgencySupplier created = AgencySupplier.builder()
                                .agency(trip.getAgency())
                                .name(item.getSupplierName().trim())
                                .category(SupplierCategory.fromServiceType(type))
                                .build();
                        supplierRepository.persist(created);
                        return created;
                    });
        }
        OperationalNextAction next = defaultNextAction(type);
        String currency = item.getCostCurrency() != null ? item.getCostCurrency()
                : (trip.getCurrency() != null ? trip.getCurrency() : "BRL");
        return OperationalService.builder()
                .trip(trip).proposalItem(item).supplier(supplier)
                .serviceType(type).name(item.getName()).subtitle(item.getSubtitle())
                .supplierName(item.getSupplierName())
                .status(OperationalServiceStatus.TO_RESERVE)
                .nextAction(next).nextActionLabel(next.defaultLabelPt())
                .nextActionDueAt(item.getQuoteExpiresAt())
                .details(item.getDetails() != null ? new LinkedHashMap<>(item.getDetails()) : new HashMap<>())
                .publicInfo(new HashMap<>())
                .costEstimatedMinor(item.getCostMinor())
                .priceApprovedMinor(item.getClientPriceMinor())
                .currency(currency)
                .sortOrder(sortOrder)
                .build();
    }

    private OperationalNextAction defaultNextAction(OperationalServiceType type) {
        return switch (type) {
            case FLIGHT -> OperationalNextAction.CHECK_AVAILABILITY;
            case HOTEL -> OperationalNextAction.CONFIRM_AVAILABILITY;
            case TRANSFER -> OperationalNextAction.CONFIRM_FLIGHT_TIME;
            case ACTIVITY -> OperationalNextAction.COLLECT_PARTICIPANTS;
            case INSURANCE -> OperationalNextAction.COLLECT_DOCUMENTS;
            case CAR_RENTAL, OTHER -> OperationalNextAction.REQUEST_RESERVATION;
        };
    }

    private boolean canIssue(OperationalServiceType type) {
        return type != null && type.supportsIssuedStatus();
    }

    private boolean isOpsEligible(Trip trip) {
        ProposalStatus s = trip.getProposalStatus();
        return s == ProposalStatus.PENDING_PAYMENT || s == ProposalStatus.CONFIRMED
                || s == ProposalStatus.IN_TRIP || s == ProposalStatus.APPROVED
                || s == ProposalStatus.COMPLETED;
    }

    private void refreshTripOperationStatus(Trip trip) {
        if (trip.getOperationStatus() == OperationStatus.CANCELLED
                && trip.getProposalStatus() == ProposalStatus.CANCELLED) {
            return;
        }
        List<OperationalService> services = serviceRepository.findByTripId(trip.id);
        trip.setOperationStatus(statusRollup.calculate(
                services, trip.getProposalStatus(), trip.getStartDate(), trip.getEndDate()));
        tripRepository.persist(trip);
    }

    private record ReadinessResult(int percent, List<TripOperationsWorkspaceDTO.ReadinessCheckDTO> checks) {}

    private ReadinessResult computeReadiness(
            List<OperationalService> services,
            List<TripPassengerResponse> passengers,
            Map<UUID, List<TripDocument>> docsByService) {
        List<TripOperationsWorkspaceDTO.ReadinessCheckDTO> checks = new ArrayList<>();
        boolean paxOk = !passengers.isEmpty() && passengers.stream().allMatch(p ->
                p.getDisplayName() != null && !p.getDisplayName().isBlank()
                        && (p.getFormStatus() == PassengerFormStatus.COMPLETE
                        || p.getFormStatus() == PassengerFormStatus.SUBMITTED
                        || p.getFormStatus() == PassengerFormStatus.IN_REVIEW));
        checks.add(check("PASSENGERS", "Passageiros cadastrados", paxOk));
        List<OperationalService> active = services.stream().filter(s -> s.getStatus().isActive()).toList();
        if (active.stream().anyMatch(s -> s.getServiceType() == OperationalServiceType.FLIGHT)) {
            checks.add(check("FLIGHT", "Voo confirmado", active.stream()
                    .filter(s -> s.getServiceType() == OperationalServiceType.FLIGHT)
                    .allMatch(s -> s.getStatus().isTerminalSuccess())));
        }
        if (active.stream().anyMatch(s -> s.getServiceType() == OperationalServiceType.HOTEL)) {
            checks.add(check("HOTEL", "Hotel confirmado", active.stream()
                    .filter(s -> s.getServiceType() == OperationalServiceType.HOTEL)
                    .allMatch(s -> s.getStatus().isTerminalSuccess())));
        }
        boolean transferPending = active.stream().anyMatch(s ->
                s.getServiceType() == OperationalServiceType.TRANSFER && !s.getStatus().isTerminalSuccess());
        checks.add(check("TRANSFER", "Transfer resolvido", !transferPending));
        boolean vouchersOk = active.stream().noneMatch(s -> s.getStatus().isTerminalSuccess())
                || active.stream().filter(s -> s.getStatus().isTerminalSuccess())
                .allMatch(s -> s.isPublished()
                        || hasReadyVoucherDoc(docsByService.getOrDefault(s.id, List.of())));
        checks.add(check("VOUCHERS", "Vouchers publicados", vouchersOk));
        long done = checks.stream().filter(TripOperationsWorkspaceDTO.ReadinessCheckDTO::isDone).count();
        int percent = checks.isEmpty() ? 0 : (int) Math.round(100.0 * done / checks.size());
        return new ReadinessResult(percent, checks);
    }

    private static boolean hasReadyVoucherDoc(List<TripDocument> docs) {
        return docs.stream().anyMatch(d ->
                VOUCHER_KINDS.contains(d.getDocumentKind())
                        && (d.getOperationalDocStatus() == OperationalDocumentStatus.SENT_TO_CLIENT
                        || d.getOperationalDocStatus() == OperationalDocumentStatus.APPROVED));
    }

    private TripOperationsWorkspaceDTO.ReadinessCheckDTO check(String code, String label, boolean done) {
        return TripOperationsWorkspaceDTO.ReadinessCheckDTO.builder().code(code).label(label).done(done).build();
    }

    private List<TripOperationsWorkspaceDTO.OperationalAlertDTO> buildAlerts(
            Trip trip, List<OperationalService> services, List<OperationalDeadline> deadlines) {
        List<TripOperationsWorkspaceDTO.OperationalAlertDTO> alerts = new ArrayList<>();
        Instant now = Instant.now();
        for (OperationalDeadline dl : deadlines) {
            if (dl.getCompletedAt() != null) continue;
            long hours = ChronoUnit.HOURS.between(now, dl.getDueAt());
            String level = dl.getDueAt().isBefore(now) || hours <= 3 ? "CRITICAL"
                    : hours <= 48 ? "WARNING" : "INFO";
            alerts.add(TripOperationsWorkspaceDTO.OperationalAlertDTO.builder()
                    .level(level)
                    .message(dl.getTitle() != null ? dl.getTitle() : String.valueOf(dl.getDeadlineType()))
                    .serviceId(dl.getService() != null ? dl.getService().id : null)
                    .dueAt(dl.getDueAt()).build());
        }
        for (OperationalService svc : services) {
            if (svc.getCostDivergenceMinor() != null && svc.getCostDivergenceMinor() != 0) {
                alerts.add(TripOperationsWorkspaceDTO.OperationalAlertDTO.builder()
                        .level("WARNING").message("Divergência de custo em " + svc.getName())
                        .serviceId(svc.id).build());
            }
            if (svc.getStatus().isActive() && !svc.getStatus().isTerminalSuccess()
                    && trip.getStartDate() != null
                    && ChronoUnit.DAYS.between(LocalDate.now(), trip.getStartDate()) <= 7) {
                alerts.add(TripOperationsWorkspaceDTO.OperationalAlertDTO.builder()
                        .level("WARNING")
                        .message("Viagem próxima: " + svc.getName() + " ainda não confirmado")
                        .serviceId(svc.id).dueAt(svc.getNextActionDueAt()).build());
            }
        }
        alerts.sort(Comparator.comparing(a -> switch (a.getLevel()) {
            case "CRITICAL" -> 0; case "WARNING" -> 1; default -> 2;
        }));
        return alerts;
    }

    private List<TripOperationsWorkspaceDTO.OperationalPendingDTO> buildPendencies(
            List<OperationalService> services, List<TripPassengerResponse> passengers) {
        List<TripOperationsWorkspaceDTO.OperationalPendingDTO> out = new ArrayList<>();
        for (TripPassengerResponse p : passengers) {
            boolean incomplete = p.getDisplayName() == null || p.getDisplayName().isBlank()
                    || p.getFormStatus() == PassengerFormStatus.NOT_REQUESTED
                    || p.getFormStatus() == PassengerFormStatus.INVITED
                    || p.getFormStatus() == PassengerFormStatus.IN_PROGRESS;
            if (incomplete) {
                out.add(TripOperationsWorkspaceDTO.OperationalPendingDTO.builder()
                        .serviceName(p.getDisplayName() != null ? p.getDisplayName() : "Passageiro")
                        .reason("Dados documentais incompletos")
                        .nextAction(OperationalNextAction.COLLECT_DOCUMENTS).build());
            }
        }
        for (OperationalService svc : services) {
            if (!svc.getStatus().isActive() || svc.getStatus().isTerminalSuccess()) continue;
            out.add(TripOperationsWorkspaceDTO.OperationalPendingDTO.builder()
                    .serviceId(svc.id).serviceName(svc.getName())
                    .reason("Status: " + svc.getStatus())
                    .nextAction(svc.getNextAction() != null ? svc.getNextAction() : OperationalNextAction.REQUEST_RESERVATION)
                    .dueAt(svc.getNextActionDueAt()).build());
        }
        return out;
    }

    private OperationalServiceDTO toServiceDto(OperationalService svc) {
        return toServiceDto(svc, documentRepository.findByOperationalServiceId(svc.id));
    }

    private OperationalServiceDTO toServiceDto(OperationalService svc, List<TripDocument> docs) {
        Long divergence = svc.getCostDivergenceMinor();
        boolean hasDivergence = divergence != null && divergence != 0;
        Map<String, Object> details = svc.getDetails();
        OperationalNextAction action = svc.getNextAction();
        List<TripDocument> safeDocs = docs != null ? docs : List.of();
        UUID voucherDocumentId = safeDocs.stream()
                .filter(d -> VOUCHER_KINDS.contains(d.getDocumentKind()))
                .map(d -> d.id)
                .findFirst()
                .orElse(null);
        List<UUID> passengerIds = svc.getPassengers() == null ? List.of()
                : svc.getPassengers().stream().map(p -> p.id).filter(Objects::nonNull).sorted().toList();
        return OperationalServiceDTO.builder()
                .id(svc.id)
                .proposalItemId(svc.getProposalItem() != null ? svc.getProposalItem().id : null)
                .serviceType(svc.getServiceType()).name(svc.getName()).subtitle(svc.getSubtitle())
                .supplierName(svc.getSupplierName())
                .supplierId(svc.getSupplier() != null ? svc.getSupplier().id : null)
                .status(svc.getStatus()).nextAction(action)
                .nextActionLabel(svc.getNextActionLabel() != null ? svc.getNextActionLabel()
                        : (action != null ? action.defaultLabelPt() : null))
                .nextActionDueAt(svc.getNextActionDueAt())
                .details(details).publicInfo(svc.getPublicInfo()).internalNotes(svc.getInternalNotes())
                .costEstimatedMinor(svc.getCostEstimatedMinor()).priceApprovedMinor(svc.getPriceApprovedMinor())
                .confirmedCostMinor(svc.getConfirmedCostMinor()).costDivergenceMinor(divergence)
                .costDivergence(hasDivergence).currency(svc.getCurrency())
                .locator(svc.getLocator()).ticketNumber(svc.getTicketNumber())
                .confirmedAt(svc.getConfirmedAt()).cancellationPolicy(svc.getCancellationPolicy())
                .published(svc.isPublished())
                .quantity(extractInt(details, "quantity", "guests", "rooms"))
                .startDate(extractDate(details, "startDate", "checkIn", "date"))
                .endDate(extractDate(details, "endDate", "checkOut"))
                .sortOrder(svc.getSortOrder())
                .passengerIds(passengerIds)
                .voucherDocumentId(voucherDocumentId)
                .documents(safeDocs.stream().map(this::toDocumentDto).toList())
                .cancelReason(svc.getCancelReason())
                .cancelledAt(svc.getCancelledAt())
                .estimatedPenaltyMinor(svc.getEstimatedPenaltyMinor())
                .supplierCreditMinor(svc.getSupplierCreditMinor())
                .build();
    }

    private OperationalDocumentDTO toDocumentDto(TripDocument doc) {
        return OperationalDocumentDTO.builder()
                .id(doc.id)
                .tripId(doc.getTrip() != null ? doc.getTrip().id : null)
                .serviceId(doc.getOperationalService() != null ? doc.getOperationalService().id : null)
                .serviceName(doc.getOperationalService() != null ? doc.getOperationalService().getName() : null)
                .title(doc.getTitle())
                .contentType(doc.getContentType())
                .documentKind(doc.getDocumentKind())
                .operationalDocStatus(doc.getOperationalDocStatus())
                .visibility(doc.getVisibility() != null ? doc.getVisibility().name() : DocumentVisibility.CLIENT.name())
                .createdAt(doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null)
                .build();
    }

    private ServiceChangeRequestDTO toChangeDto(ServiceChangeRequest change) {
        return ServiceChangeRequestDTO.builder()
                .id(change.id)
                .serviceId(change.getService() != null ? change.getService().id : null)
                .serviceName(change.getService() != null ? change.getService().getName() : null)
                .status(change.getStatus())
                .requestNote(change.getRequestNote())
                .priceDeltaMinor(change.getPriceDeltaMinor())
                .requestedByUserId(change.getRequestedBy() != null ? change.getRequestedBy().id : null)
                .requestedByName(change.getRequestedBy() != null ? change.getRequestedBy().getFullName() : null)
                .resolvedAt(change.getResolvedAt())
                .createdAt(change.getCreatedAt())
                .build();
    }

    private void clearVoucherNextAction(OperationalService svc) {
        svc.setNextAction(OperationalNextAction.NONE);
        svc.setNextActionLabel(OperationalNextAction.NONE.defaultLabelPt());
        svc.setNextActionDueAt(null);
    }

    private OperationalDeadlineDTO toDeadlineDto(OperationalDeadline dl) {
        Instant now = Instant.now();
        String level = "INFO";
        if (dl.getCompletedAt() == null) {
            long hours = ChronoUnit.HOURS.between(now, dl.getDueAt());
            if (dl.getDueAt().isBefore(now) || hours <= 3) level = "CRITICAL";
            else if (hours <= 48) level = "WARNING";
        }
        return OperationalDeadlineDTO.builder()
                .id(dl.id)
                .serviceId(dl.getService() != null ? dl.getService().id : null)
                .serviceName(dl.getService() != null ? dl.getService().getName() : null)
                .deadlineType(dl.getDeadlineType()).title(dl.getTitle()).dueAt(dl.getDueAt())
                .alertLevel(dl.getAlertLevel()).completedAt(dl.getCompletedAt())
                .computedAlertLevel(level).build();
    }

    private OperationalService requireService(Trip trip, UUID serviceId) {
        OperationalService svc = serviceRepository.findById(serviceId);
        if (svc == null || !svc.getTrip().id.equals(trip.id)) {
            throw new NotFoundException("Operational service not found");
        }
        return svc;
    }

    private Trip requireAgencyTripAccess(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findById(tripId);
        if (trip == null) throw new NotFoundException("Trip not found");
        if (trip.getAgency() == null) throw new ForbiddenException("Trip is not linked to an agency");
        AgencyMember member = agencyMemberRepository.findByAgencyAndUser(trip.getAgency().id, userId)
                .orElseThrow(() -> new ForbiddenException("Not a member of this agency"));
        if (member.getAgencyRole() == AgencyRole.AGENCY_OWNER) return trip;
        if (trip.getCreatedBy() != null && trip.getCreatedBy().id.equals(userId)) return trip;
        if (trip.getAssignedConsultant() != null && trip.getAssignedConsultant().id.equals(userId)) return trip;
        if (tripRepository.isUserLinkedToTrip(tripId, userId)) return trip;
        throw new ForbiddenException("No access to this trip");
    }

    /** Viajante (membro da trip) ou membro da agência com acesso operacional. */
    private Trip requireTripMemberOrAgencyAccess(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findById(tripId);
        if (trip == null) throw new NotFoundException("Trip not found");
        if (tripRepository.isUserLinkedToTrip(tripId, userId)) {
            return trip;
        }
        if (trip.getAgency() != null) {
            try {
                return requireAgencyTripAccess(tripId, userId);
            } catch (ForbiddenException ignored) {
                // fall through
            }
        }
        throw new ForbiddenException("No access to this trip");
    }

    private static LocalDate extractDate(Map<String, Object> details, String... keys) {
        if (details == null) return null;
        for (String key : keys) {
            Object v = details.get(key);
            if (v instanceof String s && !s.isBlank()) {
                try { return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s); }
                catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static Integer extractInt(Map<String, Object> details, String... keys) {
        if (details == null) return null;
        for (String key : keys) {
            Object v = details.get(key);
            if (v instanceof Number n) return n.intValue();
            if (v instanceof String s && !s.isBlank()) {
                try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
