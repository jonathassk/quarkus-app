package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
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

public class TripRepository implements PanacheRepository<Trip> {

    /**
     * Viagens onde o usuário é criador ou está em {@code trip_users}.
     */
    @Transactional
    public List<Trip> findAllByLinkedUserId(Long userId) {
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
    public boolean isUserLinkedToTrip(Long tripId, Long userId) {
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
    public Trip findByIdWithLock(Long id) {
        return getEntityManager().find(Trip.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    @Transactional
    public Trip updateTripUsers(Trip trip, List<User> users, Map<Long, String> userPermissions) {
        EntityManager em = getEntityManager();

        Map<Long, TripUser> currentUsers = trip.getUsers().stream()
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

    public Optional<TripUser> findTripUser(Long tripId, Long userId) {
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
    public boolean removeTripMember(Trip trip, Long userId) {
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
}
