package org.example.application.services.trip;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.trip.ActivityDTO;
import org.example.application.dto.trip.MealDTO;
import org.example.application.dto.trip.TripSegmentDTO;
import org.example.domain.entity.*;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.TripSegmentRepository;
import org.example.domain.repository.TripSegmentRevisionRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Atualização atômica de um único segmento (épico 6) com histórico para desfazer.
 */
@Slf4j
@ApplicationScoped
public class TripItineraryService {

    @Inject
    TripRepository tripRepository;
    @Inject
    TripSegmentRepository tripSegmentRepository;
    @Inject
    TripSegmentRevisionRepository revisionRepository;
    @Inject
    ObjectMapper objectMapper;

    @Transactional
    public TripSegmentDTO replaceSegment(
            UUID tripId, UUID segmentId, TripSegmentDTO body, UUID userId, String reason) {
        Trip trip = requireWritableTrip(tripId, userId);
        TripSegment segment = requireSegment(trip, segmentId);

        saveRevision(trip.id, segment, userId, reason != null ? reason : "REPLACE");

        applyDtoToSegment(segment, body);
        tripSegmentRepository.persist(segment);
        syncBudgetTotalFromItinerary(trip);
        trip.setUpdatedAt(Instant.now());
        tripRepository.persist(trip);
        return toDto(segment);
    }

    @Transactional
    public TripSegmentDTO undoSegment(UUID tripId, UUID segmentId, UUID userId) {
        Trip trip = requireWritableTrip(tripId, userId);
        TripSegment segment = requireSegment(trip, segmentId);

        TripSegmentRevision latest = revisionRepository.findLatestBySegment(segmentId)
                .orElseThrow(() -> new BadRequestException("Nenhuma revisão anterior para desfazer"));

        try {
            TripSegmentDTO previous = objectMapper.readValue(latest.getPayload(), TripSegmentDTO.class);
            applyDtoToSegment(segment, previous);
            tripSegmentRepository.persist(segment);
            syncBudgetTotalFromItinerary(trip);
            trip.setUpdatedAt(Instant.now());
            tripRepository.persist(trip);
            revisionRepository.delete(latest);
            return toDto(segment);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to restore segment revision {}", latest.id, e);
            throw new BadRequestException("Falha ao restaurar revisão do segmento");
        }
    }

    public TripSegmentDTO getSegment(UUID tripId, UUID segmentId, UUID userId) {
        Trip trip = requireReadableTrip(tripId, userId);
        return toDto(requireSegment(trip, segmentId));
    }

    private void saveRevision(UUID tripId, TripSegment segment, UUID userId, String reason) {
        try {
            String json = objectMapper.writeValueAsString(toDto(segment));
            TripSegmentRevision rev = TripSegmentRevision.builder()
                    .tripId(tripId)
                    .segmentId(segment.id)
                    .payload(json)
                    .reason(reason)
                    .createdBy(userId)
                    .build();
            revisionRepository.persist(rev);
        } catch (Exception e) {
            log.warn("Could not snapshot segment {} before replace: {}", segment.id, e.getMessage());
        }
    }

    private void applyDtoToSegment(TripSegment segment, TripSegmentDTO dto) {
        if (dto == null) {
            throw new BadRequestException("segment body is required");
        }
        if (dto.getCityId() != null && !dto.getCityId().isBlank()) {
            segment.setCityId(dto.getCityId().trim());
        }
        if (dto.getArrivalDate() != null) {
            segment.setArrivalDate(dto.getArrivalDate());
        }
        if (dto.getDepartureDate() != null) {
            segment.setDepartureDate(dto.getDepartureDate());
        }
        if (dto.getStartDay() > 0) {
            segment.setStartDay(dto.getStartDay());
        }
        if (dto.getEndDay() > 0) {
            segment.setEndDay(dto.getEndDay());
        }
        if (dto.getNotes() != null) {
            segment.setNotes(dto.getNotes());
        }
        if (dto.getDailyCost() != null) {
            segment.setDailyCost(dto.getDailyCost());
        }

        if (segment.getActivities() == null) {
            segment.setActivities(new ArrayList<>());
        } else {
            segment.getActivities().clear();
        }
        if (dto.getActivities() != null) {
            for (ActivityDTO a : dto.getActivities()) {
                if (a == null || a.getName() == null || a.getName().isBlank()) {
                    continue;
                }
                Activity activity = new Activity();
                activity.setName(a.getName());
                activity.setActivityType(a.getActivityType());
                activity.setAddress(a.getAddress());
                activity.setLatitude(a.getLatitude());
                activity.setLongitude(a.getLongitude());
                activity.setCost(a.getCost());
                activity.setStartTime(a.getStartTime());
                activity.setEndTime(a.getEndTime());
                activity.setDate(a.getDate());
                activity.setDayNumber(a.getDayNumber());
                activity.setNotes(a.getNotes());
                activity.setSegment(segment);
                segment.getActivities().add(activity);
            }
        }

        if (segment.getMeals() == null) {
            segment.setMeals(new ArrayList<>());
        } else {
            segment.getMeals().clear();
        }
        if (dto.getMeals() != null) {
            for (MealDTO m : dto.getMeals()) {
                if (m == null || m.getName() == null || m.getName().isBlank()) {
                    continue;
                }
                Meal meal = new Meal();
                meal.setName(m.getName());
                meal.setMealType(m.getMealType());
                meal.setDescription(m.getDescription());
                meal.setRestaurantName(m.getRestaurantName());
                meal.setLocation(m.getRestaurantName());
                meal.setAddress(m.getAddress());
                meal.setLatitude(m.getLatitude());
                meal.setLongitude(m.getLongitude());
                meal.setStartTime(m.getStartTime());
                meal.setEndTime(m.getEndTime());
                meal.setDate(m.getDate());
                meal.setDayNumber(m.getDayNumber());
                meal.setCost(m.getCost());
                meal.setNotes(m.getNotes());
                meal.setSegment(segment);
                segment.getMeals().add(meal);
            }
        }
    }

    public static TripSegmentDTO toDto(TripSegment segment) {
        List<ActivityDTO> acts = segment.getActivities() == null ? List.of()
                : segment.getActivities().stream().map(a -> ActivityDTO.builder()
                        .name(a.getName())
                        .activityType(a.getActivityType())
                        .address(a.getAddress())
                        .latitude(a.getLatitude())
                        .longitude(a.getLongitude())
                        .cost(a.getCost())
                        .startTime(a.getStartTime())
                        .endTime(a.getEndTime())
                        .date(a.getDate())
                        .dayNumber(a.getDayNumber())
                        .notes(a.getNotes())
                        .build()).collect(Collectors.toList());
        List<MealDTO> meals = segment.getMeals() == null ? List.of()
                : segment.getMeals().stream().map(m -> MealDTO.builder()
                        .name(m.getName())
                        .mealType(m.getMealType())
                        .description(m.getDescription())
                        .restaurantName(m.getRestaurantName())
                        .address(m.getAddress())
                        .latitude(m.getLatitude())
                        .longitude(m.getLongitude())
                        .startTime(m.getStartTime())
                        .endTime(m.getEndTime())
                        .date(m.getDate())
                        .dayNumber(m.getDayNumber())
                        .cost(m.getCost())
                        .notes(m.getNotes())
                        .build()).collect(Collectors.toList());
        return TripSegmentDTO.builder()
                .id(segment.id)
                .cityId(segment.getCityId())
                .arrivalDate(segment.getArrivalDate())
                .departureDate(segment.getDepartureDate())
                .startDay(segment.getStartDay())
                .endDay(segment.getEndDay())
                .notes(segment.getNotes())
                .dailyCost(segment.getDailyCost())
                .activities(acts)
                .meals(meals)
                .build();
    }

    /** Mantém {@code budgetTotal} = soma dos custos de atividades e refeições. */
    public static void syncBudgetTotalFromItinerary(Trip trip) {
        BigDecimal total = BigDecimal.ZERO;
        if (trip.getSegments() != null) {
            for (TripSegment segment : trip.getSegments()) {
                if (segment.getActivities() != null) {
                    for (Activity a : segment.getActivities()) {
                        if (a.getCost() != null) {
                            total = total.add(a.getCost());
                        }
                    }
                }
                if (segment.getMeals() != null) {
                    for (Meal m : segment.getMeals()) {
                        if (m.getCost() != null) {
                            total = total.add(m.getCost());
                        }
                    }
                }
            }
        }
        trip.setBudgetTotal(total);
    }

    private TripSegment requireSegment(Trip trip, UUID segmentId) {
        TripSegment segment = tripSegmentRepository.findById(segmentId);
        if (segment == null || segment.getTrip() == null || !segment.getTrip().id.equals(trip.id)) {
            throw new NotFoundException("Segment not found");
        }
        return segment;
    }

    private Trip requireWritableTrip(UUID tripId, UUID userId) {
        Trip trip = requireReadableTrip(tripId, userId);
        if (trip.getCreatedBy() != null && trip.getCreatedBy().id.equals(userId)) {
            return trip;
        }
        if (tripRepository.isUserLinkedToTrip(tripId, userId)) {
            return trip;
        }
        throw new ForbiddenException("No write access to this trip");
    }

    private Trip requireReadableTrip(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findById(tripId);
        if (trip == null) {
            throw new NotFoundException("Trip not found");
        }
        if (trip.getCreatedBy() != null && trip.getCreatedBy().id.equals(userId)) {
            return trip;
        }
        if (tripRepository.isUserLinkedToTrip(tripId, userId)) {
            return trip;
        }
        throw new ForbiddenException("No access to this trip");
    }
}
