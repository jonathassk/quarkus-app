package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.example.domain.enums.Gender;
import org.example.domain.enums.UserType;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_provider_and_id",
                        columnNames = {"provider", "provider_id"}
                )
        }
)
public class User extends PanacheEntityBase {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid")
    public UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    /** UUID do usuário no Neon Auth ({@code sub} / {@code id} do JWT). */
    @Column(name = "auth_user_id", unique = true, length = 128)
    private String authUserId;

    @Column(length = 255)
    private String city;

    @Column(length = 255)
    private String country;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(unique = true, length = 50)
    private String username;

    @Column(name = "provider", length = 20)
    private String provider; // Ex: "google", "facebook"

    @Column(name = "provider_id", length = 255)
    private String providerId;

    @Column(name = "profile_picture_url", length = 255)
    private String profilePictureUrl;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "preferred_language", length = 10)
    @Builder.Default
    private String preferredLanguage = "pt-BR";

    @Column(name = "account_status", length = 20)
    @Builder.Default
    private String accountStatus = "active";

    @Column(name = "email_verified")
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "phone_verified")
    @Builder.Default
    private Boolean phoneVerified = false;

    @Column(name = "timezone")
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender;

    @Column(name = "bio")
    private String bio;

    @Convert(converter = org.example.utils.StringListConverter.class)
    @Column(name = "visited_countries", columnDefinition = "TEXT")
    @Builder.Default
    private java.util.List<String> visitedCountries = new java.util.ArrayList<>();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "role", length = 20)
    @Builder.Default
    private String role = "USER";

    /**
     * Tipo de usuário na plataforma.
     * <ul>
     *   <li>{@link UserType#GUEST}   – criado por agência; autentica via Magic Link JWT.</li>
     *   <li>{@link UserType#FREE}    – conta B2C gratuita.</li>
     *   <li>{@link UserType#PREMIUM} – assinatura B2C ativa.</li>
     * </ul>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", length = 20, nullable = false)
    @Builder.Default
    private UserType userType = UserType.FREE;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
} 