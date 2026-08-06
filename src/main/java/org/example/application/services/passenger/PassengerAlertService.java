package org.example.application.services.passenger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.application.services.agency.AgencyAgendaService;
import org.example.domain.entity.AgencyOpportunity;
import org.example.domain.entity.Trip;
import org.example.domain.entity.TripPassenger;
import org.example.domain.entity.User;
import org.example.domain.enums.OpportunityNextActionType;
import org.example.domain.enums.OpportunityTaskPriority;
import org.example.domain.enums.PassengerDocReviewStatus;
import org.example.domain.enums.PassengerFormStatus;
import org.example.domain.repository.AgencyOpportunityRepository;
import org.example.domain.repository.TripDocumentRepository;
import org.example.domain.repository.TripPassengerRepository;
import org.example.domain.repository.TripRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Transforma alertas documentais/formulário em tarefas da agenda com prazo.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class PassengerAlertService {

    private final TripRepository tripRepository;
    private final TripPassengerRepository passengerRepository;
    private final TripDocumentRepository tripDocumentRepository;
    private final AgencyOpportunityRepository opportunityRepository;
    private final AgencyAgendaService agendaService;

    @Transactional
    public int syncAlertsForTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId);
        if (trip == null) {
            return 0;
        }
        AgencyOpportunity opp = opportunityRepository.findByTripId(tripId).orElse(null);
        if (opp == null) {
            return 0;
        }
        User assignee = opp.getAssignedConsultant() != null
                ? opp.getAssignedConsultant()
                : trip.getAssignedConsultant();
        Instant now = Instant.now();
        int created = 0;

        for (TripPassenger p : passengerRepository.findByTripId(tripId)) {
            String name = p.getDisplayName() != null ? p.getDisplayName() : "Passageiro";

            if (p.getFormStatus() == PassengerFormStatus.INVITED
                    && p.getInviteSentAt() != null
                    && p.getInviteSentAt().isBefore(now.minus(2, ChronoUnit.DAYS))) {
                if (agendaService.createPassengerAutomationTask(
                                opp,
                                OpportunityNextActionType.PASSENGER_NO_RESPONSE,
                                now.plus(1, ChronoUnit.DAYS),
                                assignee,
                                "Passageiro não respondeu: " + name,
                                OpportunityTaskPriority.IMPORTANT,
                                p.id)
                        != null) {
                    created++;
                }
            }

            if (p.getFormStatus() == PassengerFormStatus.NOT_REQUESTED
                    || p.getFormStatus() == PassengerFormStatus.INVITED
                    || p.getFormStatus() == PassengerFormStatus.IN_PROGRESS) {
                Instant due = trip.getStartDate() != null
                        ? trip.getStartDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
                                .minus(7, ChronoUnit.DAYS)
                        : now.plus(2, ChronoUnit.DAYS);
                if (due.isBefore(now)) {
                    due = now.plus(1, ChronoUnit.DAYS);
                }
                if (agendaService.createPassengerAutomationTask(
                                opp,
                                OpportunityNextActionType.PASSENGER_FORM_PENDING,
                                due,
                                assignee,
                                "Formulário pendente: " + name,
                                OpportunityTaskPriority.NORMAL,
                                p.id)
                        != null) {
                    created++;
                }
            }

            LocalDate expires = p.getDocumentExpiresAt();
            if (expires != null) {
                if (expires.isBefore(LocalDate.now())) {
                    if (agendaService.createPassengerAutomationTask(
                                    opp,
                                    OpportunityNextActionType.PASSENGER_DOC_EXPIRED,
                                    now.plus(12, ChronoUnit.HOURS),
                                    assignee,
                                    "Documento vencido: " + name,
                                    OpportunityTaskPriority.CRITICAL,
                                    p.id)
                            != null) {
                        created++;
                    }
                } else if (expires.isBefore(LocalDate.now().plusMonths(6))) {
                    if (agendaService.createPassengerAutomationTask(
                                    opp,
                                    OpportunityNextActionType.PASSENGER_DOC_EXPIRING,
                                    now.plus(3, ChronoUnit.DAYS),
                                    assignee,
                                    "Documento próximo do vencimento: " + name,
                                    OpportunityTaskPriority.IMPORTANT,
                                    p.id)
                            != null) {
                        created++;
                    }
                }
            } else if (p.getFormStatus() == PassengerFormStatus.SUBMITTED
                    || p.getFormStatus() == PassengerFormStatus.COMPLETE) {
                var docStatus = tripDocumentRepository.findLatestByPassengerId(p.id)
                        .map(d -> d.getDocReviewStatus())
                        .orElse(PassengerDocReviewStatus.NOT_PROVIDED);
                if (docStatus == PassengerDocReviewStatus.NOT_PROVIDED) {
                    if (agendaService.createPassengerAutomationTask(
                                    opp,
                                    OpportunityNextActionType.PASSENGER_FORM_PENDING,
                                    now.plus(2, ChronoUnit.DAYS),
                                    assignee,
                                    "Documento não enviado: " + name,
                                    OpportunityTaskPriority.IMPORTANT,
                                    p.id)
                            != null) {
                        created++;
                    }
                }
            }
        }
        if (created > 0) {
            log.info("Created {} passenger alert tasks for tripId={}", created, tripId);
        }
        return created;
    }

    @Transactional
    public int syncAlertsForOpportunity(AgencyOpportunity opp) {
        if (opp == null || opp.getTrip() == null) {
            return 0;
        }
        return syncAlertsForTrip(opp.getTrip().id);
    }
}
