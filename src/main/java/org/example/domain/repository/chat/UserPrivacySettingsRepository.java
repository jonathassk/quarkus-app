package org.example.domain.repository.chat;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.chat.UserPrivacySettings;

@ApplicationScoped
public class UserPrivacySettingsRepository implements PanacheRepositoryBase<UserPrivacySettings, UUID> {
}
