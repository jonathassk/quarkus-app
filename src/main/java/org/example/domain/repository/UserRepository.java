package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import org.example.domain.entity.User;

import java.util.Optional;

public class UserRepository implements PanacheRepository<User> {

    public Optional<User> findByEmail(String email) {
        return find("email", email).firstResultOptional();
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

    public User CreateUser(User user) {
        persist(user);
        return user;
    }
}
