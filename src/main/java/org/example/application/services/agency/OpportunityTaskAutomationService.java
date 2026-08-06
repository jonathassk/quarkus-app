package org.example.application.services.agency;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.example.application.services.passenger.TripPassengerService;
import org.example.domain.entity.AgencyOpportunity;
import org.example.domain.entity.AgencyOpportunityTask;
import org.example.domain.entity.Trip;
import org.example.domain.entity.User;
import org.example.domain.enums.OpportunityNextActionType;
import org.example.domain.enums.OpportunityStage;
import org.example.domain.enums.OpportunityTaskPriority;
import org.example.domain.repository.AgencyOpportunityRepository;
import org.example.domain.repository.AgencyOpportunityTaskRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Automações pré-configuradas da agenda operacional (sem editor de regras).
 */
@ApplicationScoped
public class OpportunityTaskAutomationService {

    @Inject
    AgencyAgendaService agendaService;
    @Inject
    AgencyOpportunityRepository opportunityRepository;
    @Inject
    AgencyOpportunityTaskRepository taskRepository;
    @Inject
    TripPassengerService tripPassengerService;
    @Inject
    org.example.application.services.passenger.PassengerAlertService passengerAlertService;

    @Transactional
    public void onOpportunityCreated(AgencyOpportunity opp) {
        if (opp == null) {
            return;
        }
        User assignee = opp.getAssignedConsultant();
        Instant due = Instant.now().plus(4, ChronoUnit.HOURS);
        agendaService.createAutomationTask(
                opp,
                OpportunityNextActionType.FIRST_CONTACT,
                due,
                assignee,
                OpportunityNextActionType.FIRST_CONTACT.defaultTitle(),
                OpportunityTaskPriority.IMPORTANT,
                true);
    }

    @Transactional
    public void onProposalSent(AgencyOpportunity opp, Instant expiresAt) {
        if (opp == null) {
            return;
        }
        if (taskRepository.existsOpenActionKind(opp.id, OpportunityNextActionType.FOLLOW_UP)) {
            return;
        }
        Instant due = Instant.now().plus(2, ChronoUnit.DAYS);
        if (expiresAt != null && expiresAt.isBefore(due)) {
            due = expiresAt.minus(12, ChronoUnit.HOURS);
            if (due.isBefore(Instant.now())) {
                due = Instant.now().plus(4, ChronoUnit.HOURS);
            }
        }
        User assignee = opp.getAssignedConsultant();
        if (assignee == null && opp.getNextActionAssignee() != null) {
            assignee = opp.getNextActionAssignee();
        }
        agendaService.createAutomationTask(
                opp,
                OpportunityNextActionType.FOLLOW_UP,
                due,
                assignee,
                OpportunityNextActionType.FOLLOW_UP.defaultTitle(),
                OpportunityTaskPriority.IMPORTANT,
                true);
        if (opp.getStage() == OpportunityStage.NEW
                || opp.getStage() == OpportunityStage.QUALIFYING
                || opp.getStage() == OpportunityStage.QUOTING) {
            opp.setStage(OpportunityStage.NEGOTIATING);
            opportunityRepository.persist(opp);
        }
    }

    @Transactional
    public void onProposalSentForTrip(UUID tripId, Instant expiresAt) {
        if (tripId == null) {
            return;
        }
        AgencyOpportunity opp = opportunityRepository.find("trip.id", tripId).firstResult();
        if (opp != null) {
            onProposalSent(opp, expiresAt);
        }
    }

    /**
     * Visualização: não cria tarefa. Se já houver follow-up, o DTO da agenda sugere antecipar.
     */
    @Transactional
    public void onProposalViewed(AgencyOpportunity opp) {
        // no-op for tasks; suggestion is computed in agenda DTO
    }

    @Transactional
    public void onChangeRequested(AgencyOpportunity opp) {
        if (opp == null) {
            return;
        }
        User assignee = opp.getAssignedConsultant();
        agendaService.createAutomationTask(
                opp,
                OpportunityNextActionType.REVIEW_CHANGE,
                Instant.now().plus(4, ChronoUnit.HOURS),
                assignee,
                OpportunityNextActionType.REVIEW_CHANGE.defaultTitle(),
                OpportunityTaskPriority.CRITICAL,
                true);
    }

    @Transactional
    public void onProposalApproved(AgencyOpportunity opp) {
        if (opp == null) {
            return;
        }
        User assignee = opp.getAssignedConsultant();
        agendaService.createAutomationTask(
                opp,
                OpportunityNextActionType.REQUEST_DEPOSIT,
                Instant.now().plus(1, ChronoUnit.DAYS),
                assignee,
                OpportunityNextActionType.REQUEST_DEPOSIT.defaultTitle(),
                OpportunityTaskPriority.CRITICAL,
                true);
        // Checklist mínima de reservas (secundárias)
        agendaService.createAutomationTask(
                opp,
                OpportunityNextActionType.RESERVE_SERVICE,
                Instant.now().plus(2, ChronoUnit.DAYS),
                assignee,
                "Confirmar reservas pendentes",
                OpportunityTaskPriority.IMPORTANT,
                false);
        agendaService.createAutomationTask(
                opp,
                OpportunityNextActionType.REQUEST_DOCS,
                Instant.now().plus(3, ChronoUnit.DAYS),
                assignee,
                OpportunityNextActionType.REQUEST_DOCS.defaultTitle(),
                OpportunityTaskPriority.NORMAL,
                false);

        // Seed de slots de passageiros (idempotente) para o fluxo de formulários
        if (opp.getTrip() != null && opp.getTrip().id != null) {
            tripPassengerService.seedFromOpportunityQuiet(opp.getTrip().id);
            passengerAlertService.syncAlertsForTrip(opp.getTrip().id);
        }
    }

    @Transactional
    public void onProposalApprovedForTrip(UUID tripId) {
        if (tripId == null) {
            return;
        }
        AgencyOpportunity opp = opportunityRepository.find("trip.id", tripId).firstResult();
        if (opp == null) {
            // try via commercial proposal link
            return;
        }
        onProposalApproved(opp);
    }

    /** Suggest advancing follow-up due date when proposal was just viewed. */
    @Transactional
    public boolean maybeSuggestAdvanceFollowUp(AgencyOpportunity opp, Instant viewedAt) {
        if (opp == null || viewedAt == null) {
            return false;
        }
        AgencyOpportunityTask followUp = taskRepository.findOpenFollowUp(opp.id);
        return followUp != null;
    }
}
