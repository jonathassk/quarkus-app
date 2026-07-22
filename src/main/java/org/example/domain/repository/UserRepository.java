package org.example.domain.repository;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import org.example.domain.entity.User;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class UserRepository implements PanacheRepositoryBase<User, UUID> {

    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return find("LOWER(email) = ?1", email.trim().toLowerCase(Locale.ROOT))
                .firstResultOptional();
    }

    public Optional<User> findByProviderAndId(String provider, String providerId) {
        return find("provider = ?1 and providerId = ?2", provider, providerId).firstResultOptional();
    }

    public Optional<User> findByUsername(String username) {
        return find("username", username).firstResultOptional();
    }

    public Optional<User> findByEmailOrUsername(String email, String username) {
        return find("email = ?1 or username = ?2", email, username).firstResultOptional();
    }

    public Optional<User> findByUsernameOrEmail(String email) {
        return find("email = ?1 or username = ?1", email).firstResultOptional();
    }

    public Optional<User> findByAuthUserId(String authUserId) {
        return find("authUserId", authUserId).firstResultOptional();
    }

    public User CreateUser(User user) {
        persist(user);
        return user;
    }

    /**
     * Search registered users by email, full name or username (for trip invites).
     */
    public List<User> searchForInvite(String query, UUID excludeUserId, int maxResults) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        return getEntityManager()
                .createQuery(
                        "SELECT u FROM User u WHERE u.id <> :exclude AND ("
                                + "LOWER(u.email) LIKE :q OR LOWER(u.fullName) LIKE :q "
                                + "OR LOWER(u.username) LIKE :q) ORDER BY u.fullName",
                        User.class)
                .setParameter("exclude", excludeUserId != null ? excludeUserId : new UUID(0L, 0L))
                .setParameter("q", pattern)
                .setMaxResults(maxResults)
                .getResultList();
    }
}
