package org.example.application.services.user;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.application.dto.user.UserTravelPreferencesDTO;
import org.example.domain.entity.User;
import org.example.domain.entity.UserTravelPreferences;
import org.example.domain.repository.UserRepository;
import org.example.domain.repository.UserTravelPreferencesRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
@RequiredArgsConstructor
public class TravelPreferencesService {

    private static final Set<String> ALLOWED_CURRENCIES =
            Set.of("BRL", "USD", "EUR", "GBP", "JPY", "AUD", "CAD", "CHF");
    private static final Set<String> ALLOWED_UNIT_SYSTEMS = Set.of("metric", "imperial");
    private static final Set<String> ALLOWED_SEATS = Set.of("window", "aisle", "none");
    private static final Set<String> ALLOWED_ACCOMMODATIONS = Set.of("hotel", "airbnb", "hostel");

    private final UserTravelPreferencesRepository preferencesRepository;
    private final UserRepository userRepository;

    @Transactional
    public UserTravelPreferencesDTO getPreferences(UUID userId) {
        return toDto(getOrCreate(userId));
    }

    @Transactional
    public UserTravelPreferencesDTO updatePreferences(UUID userId, UserTravelPreferencesDTO request) {
        UserTravelPreferences prefs = getOrCreate(userId);
        if (request != null) {
            apply(prefs, request);
        }
        preferencesRepository.persist(prefs);
        return toDto(prefs);
    }

    @Transactional
    public UserTravelPreferences getOrCreate(UUID userId) {
        User user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        UserTravelPreferences prefs = preferencesRepository.findById(userId);
        if (prefs == null) {
            prefs = UserTravelPreferences.builder()
                    .user(user)
                    .currency("BRL")
                    .unitSystem("metric")
                    .seatPreference("none")
                    .accommodationPreference("hotel")
                    .dietaryRestrictions(new ArrayList<>())
                    .baseAirport("")
                    .cloudBackupEnabled(false)
                    .build();
            preferencesRepository.persist(prefs);
        }
        return prefs;
    }

    private void apply(UserTravelPreferences prefs, UserTravelPreferencesDTO request) {
        if (request.getCurrency() != null) {
            String currency = request.getCurrency().trim().toUpperCase(Locale.ROOT);
            if (ALLOWED_CURRENCIES.contains(currency)) {
                prefs.setCurrency(currency);
            }
        }
        if (request.getUnitSystem() != null) {
            String unit = request.getUnitSystem().trim().toLowerCase(Locale.ROOT);
            if (ALLOWED_UNIT_SYSTEMS.contains(unit)) {
                prefs.setUnitSystem(unit);
            }
        }
        if (request.getSeatPreference() != null) {
            String seat = request.getSeatPreference().trim().toLowerCase(Locale.ROOT);
            if (ALLOWED_SEATS.contains(seat)) {
                prefs.setSeatPreference(seat);
            }
        }
        if (request.getAccommodationPreference() != null) {
            String lodging = request.getAccommodationPreference().trim().toLowerCase(Locale.ROOT);
            if (ALLOWED_ACCOMMODATIONS.contains(lodging)) {
                prefs.setAccommodationPreference(lodging);
            }
        }
        if (request.getDietaryRestrictions() != null) {
            prefs.setDietaryRestrictions(
                    request.getDietaryRestrictions().stream()
                            .filter(s -> s != null && !s.isBlank())
                            .map(String::trim)
                            .distinct()
                            .collect(Collectors.toCollection(ArrayList::new)));
        }
        if (request.getBaseAirport() != null) {
            String airport = request.getBaseAirport().trim().toUpperCase(Locale.ROOT);
            prefs.setBaseAirport(airport.length() > 3 ? airport.substring(0, 3) : airport);
        }
        prefs.setCloudBackupEnabled(request.isCloudBackupEnabled());
    }

    private UserTravelPreferencesDTO toDto(UserTravelPreferences prefs) {
        List<String> dietary =
                prefs.getDietaryRestrictions() != null
                        ? new ArrayList<>(prefs.getDietaryRestrictions())
                        : new ArrayList<>();
        return UserTravelPreferencesDTO.builder()
                .currency(prefs.getCurrency())
                .unitSystem(prefs.getUnitSystem())
                .seatPreference(prefs.getSeatPreference())
                .accommodationPreference(prefs.getAccommodationPreference())
                .dietaryRestrictions(dietary)
                .baseAirport(prefs.getBaseAirport() != null ? prefs.getBaseAirport() : "")
                .cloudBackupEnabled(prefs.isCloudBackupEnabled())
                .build();
    }
}
