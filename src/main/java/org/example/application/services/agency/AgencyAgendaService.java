package org.example.application.services.agency;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.example.application.dto.agency.AgencyAgendaDTO;
import org.example.application.dto.agency.AgencyAgendaItemDTO;
import org.example.application.dto.agency.AgencyOpportunityTaskDTO;
import org.example.application.dto.agency.CompleteOpportunityTaskRequest;
import org.example.application.dto.agency.DeferOpportunityTaskRequest;
import org.example.application.dto.agency.SetNextActionRequest;
import org.example.application.dto.agency.UpsertOpportunityTaskRequest;
import org.example.application.dto.agency.WaitingOpportunityTaskRequest;
import org.example.application.services.notification.NotificationService;
import org.example.domain.entity.AgencyClient;
import org.example.domain.entity.AgencyMember;
import org.example.domain.entity.AgencyOpportunity;
import org.example.domain.entity.AgencyOpportunityActivity;
import org.example.domain.entity.AgencyOpportunityTask;
import org.example.domain.entity.Trip;
import org.example.domain.entity.User;
import org.example.domain.enums.AgencyRole;
import org.example.domain.enums.NotificationKind;
import org.example.domain.enums.OpportunityActivityType;
import org.example.domain.enums.OpportunityNextActionType;
import org.example.domain.enums.OpportunityStage;
import org.example.domain.enums.OpportunityTaskCompletionOutcome;
import org.example.domain.enums.OpportunityTaskOrigin;
import org.example.domain.enums.OpportunityTaskPriority;
import org.example.domain.enums.OpportunityTaskStatus;
import org.example.domain.enums.OpportunityTaskType;
import org.example.domain.enums.OpportunityTaskWaitingOn;
import org.example.domain.repository.AgencyOpportunityActivityRepository;
import org.example.domain.repository.AgencyOpportunityRepository;
import org.example.domain.repository.AgencyOpportunityTaskRepository;
import org.example.domain.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AgencyAgendaService {

    @Inject
    AgencyService agencyService;
    @Inject
    AgencyOpportunityRepository opportunityRepository;
    @Inject
    AgencyOpportunityTaskRepository taskRepository;
    @Inject
    AgencyOpportunityActivityRepository activityRepository;
    @Inject
    UserRepository userRepository;
    @Inject
    NotificationService notificationService;

    public AgencyAgendaDTO getAgenda(UUID userId, UUID assigneeFilter) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        UUID agencyId = member.getAgency().id;
        boolean owner = member.getAgencyRole() == AgencyRole.AGENCY_OWNER;

        UUID effectiveAssignee = null;
        if (owner) {
            effectiveAssignee = assigneeFilter;
        } else {
            effectiveAssignee = userId;
        }

        List<AgencyOpportunityTask> openTasks = taskRepository.listOpenByAgency(agencyId);
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant startOfToday = today.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endOfToday = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<AgencyAgendaItemDTO> overdue = new ArrayList<>();
        List<AgencyAgendaItemDTO> todayList = new ArrayList<>();
        List<AgencyAgendaItemDTO> upcoming = new ArrayList<>();
        List<AgencyAgendaItemDTO> waiting = new ArrayList<>();

        for (AgencyOpportunityTask task : openTasks) {
            AgencyOpportunity opp = task.getOpportunity();
            if (opp == null || !canSeeOpportunity(member, userId, opp)) {
                continue;
            }
            if (effectiveAssignee != null && !isTaskForAssignee(task, opp, effectiveAssignee)) {
                continue;
            }
            AgencyAgendaItemDTO item = toAgendaItem(task, opp, now);

            if (task.getStatus() == OpportunityTaskStatus.WAITING) {
                waiting.add(item);
            } else if (task.getDueAt() != null && task.getDueAt().isBefore(startOfToday)) {
                overdue.add(item);
            } else if (task.getDueAt() != null
                    && !task.getDueAt().isBefore(startOfToday)
                    && task.getDueAt().isBefore(endOfToday)) {
                todayList.add(item);
            } else {
                upcoming.add(item);
            }
        }

        List<AgencyAgendaItemDTO> missing = new ArrayList<>();
        List<AgencyOpportunity> opps = opportunityRepository.list(
                "agency.id = ?1 AND stage NOT IN (?2, ?3)",
                agencyId, OpportunityStage.WON, OpportunityStage.LOST);
        for (AgencyOpportunity opp : opps) {
            if (!canSeeOpportunity(member, userId, opp)) {
                continue;
            }
            if (effectiveAssignee != null) {
                boolean match = (opp.getAssignedConsultant() != null
                        && effectiveAssignee.equals(opp.getAssignedConsultant().id))
                        || (opp.getNextActionAssignee() != null
                        && effectiveAssignee.equals(opp.getNextActionAssignee().id));
                if (!match && !owner) {
                    continue;
                }
                if (owner && assigneeFilter != null && !match) {
                    continue;
                }
            }
            if (opp.getNextActionTask() == null && opp.getNextActionAt() == null) {
                missing.add(toMissingItem(opp));
            }
        }

        return AgencyAgendaDTO.builder()
                .overdue(overdue)
                .today(todayList)
                .upcoming(upcoming)
                .waiting(waiting)
                .missingNextAction(missing)
                .summary(AgencyAgendaDTO.AgendaSummaryDTO.builder()
                        .overdueCount(overdue.size())
                        .todayCount(todayList.size())
                        .upcomingCount(upcoming.size())
                        .waitingCount(waiting.size())
                        .missingNextActionCount(missing.size())
                        .build())
                .build();
    }

    @Transactional
    public AgencyOpportunityTaskDTO createTask(
            UUID userId, UUID opportunityId, UpsertOpportunityTaskRequest request) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        AgencyOpportunity opp = requireAccessibleOpportunity(member, userId, opportunityId);
        if (request == null) {
            throw new BadRequestException("body is required");
        }

        OpportunityNextActionType actionKind = OpportunityNextActionType.fromString(request.getActionKind());
        String title = blankToNull(request.getTitle());
        if (title == null && actionKind != null) {
            title = actionKind.defaultTitle();
        }
        if (title == null) {
            throw new BadRequestException("title or actionKind is required");
        }

        User actor = userRepository.findById(userId);
        User assignee = resolveAssignee(request.getAssigneeUserId(), opp, actor);
        boolean asNext = request.getAsNextAction() == null || Boolean.TRUE.equals(request.getAsNextAction());

        OpportunityTaskType taskType = request.getTaskType() != null
                ? OpportunityTaskType.fromString(request.getTaskType())
                : (actionKind != null ? actionKind.defaultTaskType() : OpportunityTaskType.COMMERCIAL);

        AgencyOpportunityTask task = AgencyOpportunityTask.builder()
                .opportunity(opp)
                .agency(opp.getAgency())
                .title(title.trim())
                .status(OpportunityTaskStatus.OPEN)
                .dueAt(request.getDueAt())
                .assignee(assignee)
                .taskType(taskType)
                .actionKind(actionKind)
                .note(blankToNull(request.getNote()))
                .origin(OpportunityTaskOrigin.fromString(request.getOrigin()))
                .priority(OpportunityTaskPriority.fromString(request.getPriority()))
                .nextAction(false)
                .build();
        taskRepository.persist(task);

        recordActivity(opp, actor, OpportunityActivityType.TASK,
                "Tarefa criada: " + task.getTitle(), blankToNull(request.getNote()));
        opp.setLastActivityAt(Instant.now());

        if (asNext || opp.getNextActionTask() == null) {
            promoteAsNextAction(opp, task);
        }
        opportunityRepository.persist(opp);
        maybeNotifyAssignee(assignee, actor, task, opp);
        return toTaskDto(task);
    }

    @Transactional
    public AgencyOpportunityTaskDTO setNextAction(
            UUID userId, UUID opportunityId, SetNextActionRequest request) {
        if (request == null || request.getDueAt() == null) {
            throw new BadRequestException("dueAt is required");
        }
        UpsertOpportunityTaskRequest upsert = UpsertOpportunityTaskRequest.builder()
                .actionKind(request.getActionKind())
                .title(request.getTitle())
                .dueAt(request.getDueAt())
                .assigneeUserId(request.getAssigneeUserId())
                .note(request.getNote())
                .taskType(request.getTaskType())
                .priority(request.getPriority())
                .asNextAction(true)
                .origin(OpportunityTaskOrigin.MANUAL.name())
                .build();
        return createTask(userId, opportunityId, upsert);
    }

    @Transactional
    public AgencyOpportunityTaskDTO completeTask(
            UUID userId, UUID opportunityId, UUID taskId, CompleteOpportunityTaskRequest request) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        AgencyOpportunity opp = requireAccessibleOpportunity(member, userId, opportunityId);
        AgencyOpportunityTask task = requireTask(opportunityId, taskId);
        User actor = userRepository.findById(userId);

        OpportunityTaskCompletionOutcome outcome = request != null
                ? OpportunityTaskCompletionOutcome.fromString(request.getOutcome())
                : null;

        task.setStatus(OpportunityTaskStatus.DONE);
        task.setCompletedAt(Instant.now());
        task.setCompletedBy(actor);
        task.setCompletionOutcome(outcome);
        if (request != null && blankToNull(request.getNote()) != null) {
            task.setNote(blankToNull(request.getNote()));
        }
        task.setWaitingOn(null);
        task.setNextAction(false);
        taskRepository.persist(task);

        String outcomeLabel = outcome != null ? outcome.name() : "DONE";
        recordActivity(opp, actor, OpportunityActivityType.TASK,
                "Ação concluída: " + task.getTitle(),
                outcomeLabel + (request != null && request.getNote() != null ? " — " + request.getNote() : ""));

        if (opp.getNextActionTask() != null && task.id.equals(opp.getNextActionTask().id)) {
            clearNextActionProjection(opp);
        }

        boolean defineNext = request != null && (
                Boolean.TRUE.equals(request.getDefineNext())
                        || request.getNextActionKind() != null
                        || request.getNextDueAt() != null);
        if (defineNext && request != null && !Boolean.FALSE.equals(request.getDefineNext())) {
            if (request.getNextDueAt() == null) {
                throw new BadRequestException("nextDueAt is required when defining next step");
            }
            UpsertOpportunityTaskRequest next = UpsertOpportunityTaskRequest.builder()
                    .actionKind(request.getNextActionKind())
                    .title(request.getNextTitle())
                    .dueAt(request.getNextDueAt())
                    .assigneeUserId(request.getNextAssigneeUserId() != null
                            ? request.getNextAssigneeUserId()
                            : (task.getAssignee() != null ? task.getAssignee().id : userId))
                    .note(request.getNextNote())
                    .asNextAction(true)
                    .origin(OpportunityTaskOrigin.MANUAL.name())
                    .build();
            createTask(userId, opportunityId, next);
        } else {
            opportunityRepository.persist(opp);
        }
        return toTaskDto(taskRepository.findById(taskId));
    }

    @Transactional
    public AgencyOpportunityTaskDTO deferTask(
            UUID userId, UUID opportunityId, UUID taskId, DeferOpportunityTaskRequest request) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        AgencyOpportunity opp = requireAccessibleOpportunity(member, userId, opportunityId);
        AgencyOpportunityTask task = requireTask(opportunityId, taskId);
        if (request == null || request.getDueAt() == null) {
            throw new BadRequestException("dueAt is required");
        }
        task.setDueAt(request.getDueAt());
        if (task.getStatus() == OpportunityTaskStatus.WAITING) {
            // keep waiting, just move review date
        } else {
            task.setStatus(OpportunityTaskStatus.OPEN);
        }
        taskRepository.persist(task);
        if (task.isNextAction()) {
            syncProjection(opp, task);
            opportunityRepository.persist(opp);
        }
        recordActivity(opp, userRepository.findById(userId), OpportunityActivityType.TASK,
                "Ação adiada: " + task.getTitle(), null);
        opp.setLastActivityAt(Instant.now());
        return toTaskDto(task);
    }

    @Transactional
    public AgencyOpportunityTaskDTO markWaiting(
            UUID userId, UUID opportunityId, UUID taskId, WaitingOpportunityTaskRequest request) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        AgencyOpportunity opp = requireAccessibleOpportunity(member, userId, opportunityId);
        AgencyOpportunityTask task = requireTask(opportunityId, taskId);
        if (request == null || request.getDueAt() == null) {
            throw new BadRequestException("dueAt (review date) is required");
        }
        OpportunityTaskWaitingOn waitingOn = OpportunityTaskWaitingOn.fromString(request.getWaitingOn());
        if (waitingOn == null) {
            throw new BadRequestException("waitingOn is required");
        }
        task.setStatus(OpportunityTaskStatus.WAITING);
        task.setWaitingOn(waitingOn);
        task.setDueAt(request.getDueAt());
        if (blankToNull(request.getNote()) != null) {
            task.setNote(blankToNull(request.getNote()));
        }
        taskRepository.persist(task);
        if (task.isNextAction()) {
            syncProjection(opp, task);
            opportunityRepository.persist(opp);
        }
        recordActivity(opp, userRepository.findById(userId), OpportunityActivityType.TASK,
                "Aguardando " + waitingOn.name().toLowerCase() + ": " + task.getTitle(),
                blankToNull(request.getNote()));
        opp.setLastActivityAt(Instant.now());
        return toTaskDto(task);
    }

    @Transactional
    public AgencyOpportunityTaskDTO updateTask(
            UUID userId, UUID opportunityId, UUID taskId, UpsertOpportunityTaskRequest request) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        AgencyOpportunity opp = requireAccessibleOpportunity(member, userId, opportunityId);
        AgencyOpportunityTask task = requireTask(opportunityId, taskId);
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
            maybeNotifyAssignee(assignee, userRepository.findById(userId), task, opp);
        }
        if (request.getNote() != null) {
            task.setNote(blankToNull(request.getNote()));
        }
        if (request.getActionKind() != null) {
            task.setActionKind(OpportunityNextActionType.fromString(request.getActionKind()));
        }
        if (request.getTaskType() != null) {
            task.setTaskType(OpportunityTaskType.fromString(request.getTaskType()));
        }
        if (request.getPriority() != null) {
            task.setPriority(OpportunityTaskPriority.fromString(request.getPriority()));
        }
        if (request.getStatus() != null) {
            OpportunityTaskStatus status = OpportunityTaskStatus.fromString(request.getStatus());
            task.setStatus(status);
            if (status == OpportunityTaskStatus.DONE) {
                task.setCompletedAt(Instant.now());
                task.setCompletedBy(userRepository.findById(userId));
                task.setNextAction(false);
                if (opp.getNextActionTask() != null && task.id.equals(opp.getNextActionTask().id)) {
                    clearNextActionProjection(opp);
                }
            } else if (status == OpportunityTaskStatus.CANCELLED) {
                task.setNextAction(false);
                if (opp.getNextActionTask() != null && task.id.equals(opp.getNextActionTask().id)) {
                    clearNextActionProjection(opp);
                }
            } else {
                task.setCompletedAt(null);
            }
        }
        if (Boolean.TRUE.equals(request.getAsNextAction())) {
            promoteAsNextAction(opp, task);
        }
        taskRepository.persist(task);
        if (task.isNextAction()) {
            syncProjection(opp, task);
        }
        opportunityRepository.persist(opp);
        return toTaskDto(task);
    }

    @Transactional
    public void deleteTask(UUID userId, UUID opportunityId, UUID taskId) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        AgencyOpportunity opp = requireAccessibleOpportunity(member, userId, opportunityId);
        AgencyOpportunityTask task = requireTask(opportunityId, taskId);
        if (opp.getNextActionTask() != null && task.id.equals(opp.getNextActionTask().id)) {
            clearNextActionProjection(opp);
            opportunityRepository.persist(opp);
        }
        taskRepository.delete(task);
    }

    /** Internal: create automated task without HTTP user scoping. */
    @Transactional
    public AgencyOpportunityTask createAutomationTask(
            AgencyOpportunity opp,
            OpportunityNextActionType actionKind,
            Instant dueAt,
            User assignee,
            String title,
            OpportunityTaskPriority priority,
            boolean asNextAction) {
        if (actionKind != null && taskRepository.existsOpenAutomation(opp.id, actionKind)) {
            return null;
        }
        if (actionKind == OpportunityNextActionType.FOLLOW_UP
                && taskRepository.existsOpenActionKind(opp.id, OpportunityNextActionType.FOLLOW_UP)) {
            return null;
        }
        AgencyOpportunityTask task = AgencyOpportunityTask.builder()
                .opportunity(opp)
                .agency(opp.getAgency())
                .title(title != null ? title : (actionKind != null ? actionKind.defaultTitle() : "Ação"))
                .status(OpportunityTaskStatus.OPEN)
                .dueAt(dueAt)
                .assignee(assignee)
                .taskType(actionKind != null ? actionKind.defaultTaskType() : OpportunityTaskType.COMMERCIAL)
                .actionKind(actionKind)
                .origin(OpportunityTaskOrigin.AUTOMATION)
                .priority(priority != null ? priority : OpportunityTaskPriority.NORMAL)
                .nextAction(false)
                .build();
        taskRepository.persist(task);
        if (asNextAction || opp.getNextActionTask() == null) {
            promoteAsNextAction(opp, task);
        }
        opportunityRepository.persist(opp);
        recordActivity(opp, null, OpportunityActivityType.TASK,
                "Tarefa automática: " + task.getTitle(), null);
        if (assignee != null) {
            maybeNotifyAssignee(assignee, null, task, opp);
        }
        return task;
    }

    /**
     * Automação por passageiro (idempotente via note com passengerId=...).
     * Não usa {@link #existsOpenAutomation} global — permite uma tarefa por passageiro.
     */
    @Transactional
    public AgencyOpportunityTask createPassengerAutomationTask(
            AgencyOpportunity opp,
            OpportunityNextActionType actionKind,
            Instant dueAt,
            User assignee,
            String title,
            OpportunityTaskPriority priority,
            UUID passengerId) {
        if (opp == null || actionKind == null || passengerId == null) {
            return null;
        }
        if (taskRepository.existsOpenAutomationForPassengerNote(opp.id, actionKind, passengerId)) {
            return null;
        }
        AgencyOpportunityTask task = AgencyOpportunityTask.builder()
                .opportunity(opp)
                .agency(opp.getAgency())
                .title(title != null ? title : actionKind.defaultTitle())
                .note("passengerId=" + passengerId)
                .status(OpportunityTaskStatus.OPEN)
                .dueAt(dueAt)
                .assignee(assignee)
                .taskType(actionKind.defaultTaskType())
                .actionKind(actionKind)
                .origin(OpportunityTaskOrigin.AUTOMATION)
                .priority(priority != null ? priority : OpportunityTaskPriority.NORMAL)
                .nextAction(false)
                .build();
        taskRepository.persist(task);
        opportunityRepository.persist(opp);
        recordActivity(opp, null, OpportunityActivityType.TASK,
                "Alerta passageiro: " + task.getTitle(), null);
        if (assignee != null) {
            maybeNotifyAssignee(assignee, null, task, opp);
        }
        return task;
    }

    public void promoteAsNextAction(AgencyOpportunity opp, AgencyOpportunityTask task) {
        if (opp.getNextActionTask() != null && !opp.getNextActionTask().id.equals(task.id)) {
            AgencyOpportunityTask prev = opp.getNextActionTask();
            prev.setNextAction(false);
            taskRepository.persist(prev);
        }
        // clear other flags on same opportunity
        for (AgencyOpportunityTask t : taskRepository.listByOpportunity(opp.id)) {
            if (!t.id.equals(task.id) && t.isNextAction()) {
                t.setNextAction(false);
                taskRepository.persist(t);
            }
        }
        task.setNextAction(true);
        taskRepository.persist(task);
        syncProjection(opp, task);
    }

    public void syncProjection(AgencyOpportunity opp, AgencyOpportunityTask task) {
        opp.setNextActionTask(task);
        opp.setNextActionType(task.getActionKind());
        opp.setNextActionAt(task.getDueAt());
        opp.setNextActionNote(task.getNote() != null ? task.getNote() : task.getTitle());
        opp.setNextActionAssignee(task.getAssignee());
    }

    public void clearNextActionProjection(AgencyOpportunity opp) {
        opp.setNextActionTask(null);
        opp.setNextActionType(null);
        opp.setNextActionAt(null);
        opp.setNextActionNote(null);
        opp.setNextActionAssignee(null);
    }

    public AgencyOpportunityTaskDTO toTaskDto(AgencyOpportunityTask task) {
        if (task == null) {
            return null;
        }
        User assignee = task.getAssignee();
        boolean overdue = task.getStatus() != null
                && task.getStatus().isOpenLike()
                && task.getDueAt() != null
                && task.getDueAt().isBefore(Instant.now());
        User completedBy = task.getCompletedBy();
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
                .taskType(task.getTaskType() != null ? task.getTaskType().name() : null)
                .actionKind(task.getActionKind() != null ? task.getActionKind().name() : null)
                .note(task.getNote())
                .waitingOn(task.getWaitingOn() != null ? task.getWaitingOn().name() : null)
                .nextAction(task.isNextAction())
                .origin(task.getOrigin() != null ? task.getOrigin().name() : null)
                .priority(task.getPriority() != null ? task.getPriority().name() : null)
                .completionOutcome(task.getCompletionOutcome() != null
                        ? task.getCompletionOutcome().name() : null)
                .completedByUserId(completedBy != null ? completedBy.id : null)
                .completedAt(task.getCompletedAt())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .overdue(overdue)
                .build();
    }

    private void maybeNotifyAssignee(
            User assignee, User actor, AgencyOpportunityTask task, AgencyOpportunity opp) {
        if (assignee == null) {
            return;
        }
        if (actor != null && assignee.id.equals(actor.id)) {
            return;
        }
        try {
            notificationService.create(
                    assignee.id,
                    NotificationKind.AGENDA_TASK_ASSIGNED,
                    "Nova ação na agenda",
                    task.getTitle() + (opp.getClient() != null ? " · " + opp.getClient().getName() : ""),
                    "opportunity",
                    opp.id,
                    false,
                    "/business/opportunities/" + opp.id);
        } catch (Exception ignored) {
            // best-effort
        }
    }

    /** Notify assignee when a next-action task is overdue by more than 1 day (OWNER escalation light). */
    public void notifyIfSeverelyOverdue(AgencyOpportunityTask task, AgencyOpportunity opp) {
        if (task == null || task.getDueAt() == null || task.getAssignee() == null) {
            return;
        }
        if (!task.getStatus().isOpenLike()) {
            return;
        }
        if (task.getDueAt().isAfter(Instant.now().minus(1, ChronoUnit.DAYS))) {
            return;
        }
        try {
            notificationService.create(
                    task.getAssignee().id,
                    NotificationKind.AGENDA_TASK_OVERDUE,
                    "Ação atrasada",
                    task.getTitle() + (opp.getClient() != null ? " · " + opp.getClient().getName() : ""),
                    "opportunity",
                    opp.id,
                    false,
                    "/business/agenda");
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private AgencyAgendaItemDTO toAgendaItem(AgencyOpportunityTask task, AgencyOpportunity opp, Instant now) {
        AgencyClient client = opp.getClient();
        User assignee = task.getAssignee();
        Trip trip = opp.getTrip();
        boolean overdue = task.getStatus() == OpportunityTaskStatus.OPEN
                && task.getDueAt() != null
                && task.getDueAt().isBefore(now);
        boolean suggestAdvance = false;
        Instant viewedAt = trip != null ? trip.getProposalLastViewedAt() : null;
        if (task.getActionKind() == OpportunityNextActionType.FOLLOW_UP
                && viewedAt != null
                && viewedAt.isAfter(now.minus(48, ChronoUnit.HOURS))) {
            suggestAdvance = true;
        }
        String recentEvent = null;
        Instant recentEventAt = opp.getLastActivityAt();
        if (viewedAt != null && (recentEventAt == null || viewedAt.isAfter(recentEventAt))) {
            recentEvent = "Proposta visualizada";
            recentEventAt = viewedAt;
        } else if (recentEventAt != null) {
            recentEvent = "Última atividade";
        }
        return AgencyAgendaItemDTO.builder()
                .taskId(task.id)
                .opportunityId(opp.id)
                .title(task.getTitle())
                .actionKind(task.getActionKind() != null ? task.getActionKind().name() : null)
                .status(task.getStatus() != null ? task.getStatus().name() : null)
                .dueAt(task.getDueAt())
                .overdue(overdue)
                .nextAction(task.isNextAction())
                .priority(task.getPriority() != null ? task.getPriority().name() : null)
                .waitingOn(task.getWaitingOn() != null ? task.getWaitingOn().name() : null)
                .note(task.getNote())
                .assigneeUserId(assignee != null ? assignee.id : null)
                .assigneeName(assignee != null
                        ? (assignee.getFullName() != null ? assignee.getFullName() : assignee.getEmail())
                        : null)
                .clientName(client != null ? client.getName() : null)
                .clientPhone(client != null ? client.getPhone() : null)
                .destination(blankToNull(opp.getDestinations()) != null
                        ? opp.getDestinations()
                        : blankToNull(opp.getCity()))
                .opportunityTitle(opp.getTitle())
                .stage(opp.getStage() != null ? opp.getStage().name() : null)
                .recentEvent(recentEvent)
                .recentEventAt(recentEventAt)
                .suggestAdvanceFollowUp(suggestAdvance)
                .build();
    }

    private AgencyAgendaItemDTO toMissingItem(AgencyOpportunity opp) {
        AgencyClient client = opp.getClient();
        User consultant = opp.getAssignedConsultant();
        return AgencyAgendaItemDTO.builder()
                .opportunityId(opp.id)
                .title("Definir próxima ação")
                .status("MISSING")
                .nextAction(false)
                .clientName(client != null ? client.getName() : null)
                .clientPhone(client != null ? client.getPhone() : null)
                .destination(blankToNull(opp.getDestinations()) != null
                        ? opp.getDestinations()
                        : blankToNull(opp.getCity()))
                .opportunityTitle(opp.getTitle())
                .stage(opp.getStage() != null ? opp.getStage().name() : null)
                .assigneeUserId(consultant != null ? consultant.id : null)
                .assigneeName(consultant != null
                        ? (consultant.getFullName() != null ? consultant.getFullName() : consultant.getEmail())
                        : null)
                .recentEventAt(opp.getLastActivityAt())
                .build();
    }

    private static boolean isTaskForAssignee(
            AgencyOpportunityTask task, AgencyOpportunity opp, UUID assigneeId) {
        if (task.getAssignee() != null && assigneeId.equals(task.getAssignee().id)) {
            return true;
        }
        return opp.getAssignedConsultant() != null && assigneeId.equals(opp.getAssignedConsultant().id);
    }

    private static boolean canSeeOpportunity(AgencyMember member, UUID userId, AgencyOpportunity opp) {
        if (member.getAgencyRole() == AgencyRole.AGENCY_OWNER) {
            return true;
        }
        return (opp.getAssignedConsultant() != null && userId.equals(opp.getAssignedConsultant().id))
                || (opp.getNextActionAssignee() != null && userId.equals(opp.getNextActionAssignee().id));
    }

    private AgencyOpportunity requireAccessibleOpportunity(
            AgencyMember member, UUID userId, UUID opportunityId) {
        AgencyOpportunity opp = opportunityRepository.findById(opportunityId);
        if (opp == null || opp.getAgency() == null || !opp.getAgency().id.equals(member.getAgency().id)) {
            throw new NotFoundException("Opportunity not found");
        }
        if (member.getAgencyRole() == AgencyRole.AGENCY_CONSULTANT && !canSeeOpportunity(member, userId, opp)) {
            throw new ForbiddenException("You do not have access to this opportunity");
        }
        return opp;
    }

    private AgencyOpportunityTask requireTask(UUID opportunityId, UUID taskId) {
        AgencyOpportunityTask task = taskRepository.findById(taskId);
        if (task == null || task.getOpportunity() == null || !opportunityId.equals(task.getOpportunity().id)) {
            throw new NotFoundException("Task not found");
        }
        return task;
    }

    private User resolveAssignee(UUID assigneeUserId, AgencyOpportunity opp, User actor) {
        if (assigneeUserId != null) {
            User assignee = userRepository.findById(assigneeUserId);
            if (assignee == null) {
                throw new NotFoundException("Assignee not found");
            }
            return assignee;
        }
        if (opp.getAssignedConsultant() != null) {
            return opp.getAssignedConsultant();
        }
        return actor;
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

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
