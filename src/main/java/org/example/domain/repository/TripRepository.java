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
import java.util.stream.Collectors;

public class TripRepository implements PanacheRepository<Trip> {
    
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
}
