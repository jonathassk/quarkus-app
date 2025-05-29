package org.example.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "meals")
public class Meal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "daily_itinerary_id")
    private Long dailyItineraryId;

    @Column(name = "meal_type", nullable = false, length = 20)
    private String mealType;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String location;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(precision = 10, scale = 2)
    private BigDecimal cost;

    @Column(nullable = false)
    private LocalDate date;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_segment_id")
    private TripSegment tripSegment;

    @Column(name = "booking_reference", length = 100)
    private String bookingReference;
} 