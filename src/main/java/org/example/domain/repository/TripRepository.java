package org.example.domain.repository;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.example.domain.entity.Trip;
import org.example.domain.entity.TripUser;
import org.example.domain.entity.User;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class TripRepository implements PanacheRepositoryBase<Trip, UUID> {

    /**
     * Viagens onde o usuário é criador ou está em {@code trip_users}.
     */
    @Transactional
    public List<Trip> findAllByLinkedUserId(UUID userId) {
        return getEntityManager()
                .createQuery(
                        "SELECT DISTINCT t FROM Trip t LEFT JOIN t.users u "
                                + "WHERE t.createdBy.id = :uid OR u.user.id = :uid "
                                + "ORDER BY t.startDate DESC NULLS LAST, t.id DESC",
                        Trip.class)
                .setParameter("uid", userId)
                .getResultList();
    }

    /** Criador da viagem ou participante em {@code trip_users}. */
    public boolean isUserLinkedToTrip(UUID tripId, UUID userId) {
        Long count = getEntityManager()
                .createQuery(
                        "SELECT COUNT(t) FROM Trip t WHERE t.id = :tid AND ("
                                + "t.createdBy.id = :uid OR EXISTS (SELECT 1 FROM TripUser tu "
                                + "WHERE tu.trip.id = t.id AND tu.user.id = :uid))",
                        Long.class)
                .setParameter("tid", tripId)
                .setParameter("uid", userId)
                .getSingleResult();
        return count != null && count > 0;
    }
    
    @Transactional
    public Trip findByIdWithLock(UUID id) {
        return getEntityManager().find(Trip.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    @Transactional
    public Trip updateTripUsers(Trip trip, List<User> users, Map<UUID, String> userPermissions) {
        EntityManager em = getEntityManager();

        if (trip.getUsers() == null) {
            trip.setUsers(new java.util.ArrayList<>());
        }

        Map<UUID, TripUser> currentUsers = trip.getUsers().stream()
            .collect(Collectors.toMap(tu -> tu.getUser().id, tu -> tu));

        for (User user : users) {
            TripUser existingTripUser = currentUsers.get(user.id);
            if (existingTripUser != null) {
                existingTripUser.setPermissionLevel(userPermissions.get(user.id));
            } else {
                TripUser newTripUser = TripUser.builder()
                    .trip(trip)
                    .user(user)
                    .permissionLevel(userPermissions.get(user.id))
                    .build();
                em.persist(newTripUser);
                trip.getUsers().add(newTripUser);
            }
        }

        List<TripUser> usersToRemove = trip.getUsers().stream()
            .filter(tu -> !users.stream().anyMatch(u -> u.id.equals(tu.getUser().id)))
            .toList();

        for (TripUser userToRemove : usersToRemove) {
            trip.getUsers().remove(userToRemove);
            em.remove(userToRemove);
        }

        return em.merge(trip);
    }

    @Transactional
    public Trip updateTrip(Trip trip) {
        return getEntityManager().merge(trip);
    }

    public Optional<TripUser> findTripUser(UUID tripId, UUID userId) {
        return getEntityManager()
                .createQuery(
                        "SELECT tu FROM TripUser tu WHERE tu.trip.id = :tid AND tu.user.id = :uid",
                        TripUser.class)
                .setParameter("tid", tripId)
                .setParameter("uid", userId)
                .getResultStream()
                .findFirst();
    }

    @Transactional
    public TripUser addTripMember(Trip trip, User user, String permissionLevel) {
        TripUser tripUser =
                TripUser.builder().trip(trip).user(user).permissionLevel(permissionLevel).build();
        getEntityManager().persist(tripUser);
        if (trip.getUsers() == null) {
            trip.setUsers(new java.util.ArrayList<>());
        }
        trip.getUsers().add(tripUser);
        return tripUser;
    }

    @Transactional
    public boolean removeTripMember(Trip trip, UUID userId) {
        Optional<TripUser> tripUser = findTripUser(trip.id, userId);
        if (tripUser.isEmpty()) {
            return false;
        }
        TripUser tu = tripUser.get();
        if (trip.getUsers() != null) {
            trip.getUsers().remove(tu);
        }
        getEntityManager().remove(tu);
        return true;
    }

    public int countTripMembers(UUID tripId) {
        Number count =
                (Number)
                        getEntityManager()
                                .createNativeQuery("SELECT count_trip_members(?1)")
                                .setParameter(1, tripId)
                                .getSingleResult();
        return count != null ? count.intValue() : 0;
    }

    public List<UUID> listTripMemberUserIds(UUID tripId) {
        Trip trip = findById(tripId);
        if (trip == null) {
            return List.of();
        }
        java.util.LinkedHashSet<UUID> memberIds = new java.util.LinkedHashSet<>();
        if (trip.getCreatedBy() != null) {
            memberIds.add(trip.getCreatedBy().id);
        }
        if (trip.getUsers() != null) {
            for (TripUser tu : trip.getUsers()) {
                if (tu.getUser() != null) {
                    memberIds.add(tu.getUser().id);
                }
            }
        }
        return new java.util.ArrayList<>(memberIds);
    }

    public Optional<Trip> findByShareCode(String shareCode) {
        if (shareCode == null || shareCode.isBlank()) {
            return Optional.empty();
        }
        return find("shareCode", shareCode.trim()).firstResultOptional();
    }

    public List<Trip> findByAgencyId(UUID agencyId) {
        return list("agency.id = ?1 ORDER BY updatedAt DESC", agencyId);
    }

    /**
     * Trips da agência para o picker de roteiros (exclui demo).
     * Retorna pares {@code [Trip, segmentCount]}.
     */
    public List<Object[]> findForAgencyPicker(
            UUID agencyId, String q, java.util.Collection<UUID> excludeTripIds, int limit) {
        StringBuilder jpql = new StringBuilder(
                "SELECT t, SIZE(t.segments) FROM Trip t "
                        + "WHERE t.agency.id = :agencyId AND t.demo = false");
        if (q != null && !q.isBlank()) {
            jpql.append(" AND lower(t.name) LIKE :q");
        }
        if (excludeTripIds != null && !excludeTripIds.isEmpty()) {
            jpql.append(" AND t.id NOT IN :excludeIds");
        }
        jpql.append(" ORDER BY t.updatedAt DESC");
        var query = getEntityManager().createQuery(jpql.toString(), Object[].class);
        query.setParameter("agencyId", agencyId);
        if (q != null && !q.isBlank()) {
            query.setParameter("q", "%" + q.trim().toLowerCase() + "%");
        }
        if (excludeTripIds != null && !excludeTripIds.isEmpty()) {
            query.setParameter("excludeIds", excludeTripIds);
        }
        query.setMaxResults(Math.min(Math.max(limit, 1), 100));
        return query.getResultList();
    }

    public List<Trip> findByAgencyIdAndProposalStatus(UUID agencyId, org.example.domain.enums.ProposalStatus status) {
        return list("agency.id = ?1 AND proposalStatus = ?2 ORDER BY updatedAt DESC", agencyId, status);
    }

    public List<Trip> findByClientId(UUID clientId) {
        return list("client.id = ?1 ORDER BY updatedAt DESC", clientId);
    }

    /**
     * Pipeline com filtros e paginação no banco.
     *
     * @param scopeUserId quando não-null, restringe a criador ou consultor atribuído
     * @param scope ACTIVE (kanban), ARCHIVE (histórico) ou ALL
     */
    public List<Trip> findPipeline(
            UUID agencyId,
            org.example.domain.enums.ProposalStatus status,
            UUID consultantId,
            String q,
            UUID scopeUserId,
            org.example.domain.enums.PipelineScope scope,
            int page,
            int size) {
        StringBuilder jpql = new StringBuilder("FROM Trip t WHERE t.agency.id = :agencyId");
        var em = getEntityManager();
        appendPipelineFilters(jpql, status, consultantId, q, scopeUserId, scope);
        jpql.append(" ORDER BY t.updatedAt DESC");
        var query = em.createQuery(jpql.toString(), Trip.class);
        bindPipelineParams(query, agencyId, status, consultantId, q, scopeUserId, scope);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        query.setFirstResult(safePage * safeSize);
        query.setMaxResults(safeSize);
        return query.getResultList();
    }

    public long countPipeline(
            UUID agencyId,
            org.example.domain.enums.ProposalStatus status,
            UUID consultantId,
            String q,
            UUID scopeUserId,
            org.example.domain.enums.PipelineScope scope) {
        StringBuilder jpql = new StringBuilder("SELECT COUNT(t) FROM Trip t WHERE t.agency.id = :agencyId");
        var em = getEntityManager();
        appendPipelineFilters(jpql, status, consultantId, q, scopeUserId, scope);
        var query = em.createQuery(jpql.toString(), Long.class);
        bindPipelineParams(query, agencyId, status, consultantId, q, scopeUserId, scope);
        Long count = query.getSingleResult();
        return count != null ? count : 0L;
    }

    private void appendPipelineFilters(
            StringBuilder jpql,
            org.example.domain.enums.ProposalStatus status,
            UUID consultantId,
            String q,
            UUID scopeUserId,
            org.example.domain.enums.PipelineScope scope) {
        if (status != null) {
            jpql.append(" AND t.proposalStatus = :status");
        } else if (scope == org.example.domain.enums.PipelineScope.ACTIVE) {
            jpql.append(" AND t.proposalStatus IN :scopeStatuses");
        } else if (scope == org.example.domain.enums.PipelineScope.ARCHIVE) {
            jpql.append(" AND t.proposalStatus IN :scopeStatuses");
        }
        if (consultantId != null) {
            jpql.append(" AND t.assignedConsultant.id = :consultantId");
        }
        if (scopeUserId != null) {
            jpql.append(" AND (t.createdBy.id = :scopeUserId OR t.assignedConsultant.id = :scopeUserId)");
        }
        if (q != null && !q.isBlank()) {
            jpql.append(" AND (lower(t.name) LIKE :q OR lower(coalesce(t.proposalClientEmail,'')) LIKE :q"
                    + " OR lower(coalesce(t.proposalClientName,'')) LIKE :q)");
        }
    }

    private void bindPipelineParams(
            jakarta.persistence.Query query,
            UUID agencyId,
            org.example.domain.enums.ProposalStatus status,
            UUID consultantId,
            String q,
            UUID scopeUserId,
            org.example.domain.enums.PipelineScope scope) {
        query.setParameter("agencyId", agencyId);
        if (status != null) {
            query.setParameter("status", status);
        } else if (scope == org.example.domain.enums.PipelineScope.ACTIVE) {
            query.setParameter("scopeStatuses", org.example.domain.enums.ProposalStatus.ACTIVE_PIPELINE);
        } else if (scope == org.example.domain.enums.PipelineScope.ARCHIVE) {
            query.setParameter("scopeStatuses", org.example.domain.enums.ProposalStatus.ARCHIVE);
        }
        if (consultantId != null) {
            query.setParameter("consultantId", consultantId);
        }
        if (scopeUserId != null) {
            query.setParameter("scopeUserId", scopeUserId);
        }
        if (q != null && !q.isBlank()) {
            query.setParameter("q", "%" + q.trim().toLowerCase() + "%");
        }
    }

    /** Viagens ativas do usuário (criador ou membro) vinculadas a um workspace. */
    public long countActiveByUserAndWorkspace(UUID userId, UUID workspaceId) {
        Long count = getEntityManager()
                .createQuery(
                        "SELECT COUNT(DISTINCT t) FROM Trip t LEFT JOIN t.users u "
                                + "WHERE t.workspace.id = :wid AND (t.createdBy.id = :uid OR u.user.id = :uid)",
                        Long.class)
                .setParameter("wid", workspaceId)
                .setParameter("uid", userId)
                .getSingleResult();
        return count != null ? count : 0L;
    }
}
