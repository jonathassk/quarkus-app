package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.UserEmailPreferences;

import java.util.UUID;

@ApplicationScoped
public class UserEmailPreferencesRepository implements PanacheRepositoryBase<UserEmailPreferences, UUID> {
}
