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
@Table(name = "activities")
public class Activity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "daily_itinerary_id")
    private Long dailyItineraryId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "activity_type", nullable = false, length = 50)
    private String activityType;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(precision = 10, scale = 2)
    private BigDecimal cost;

    @Column(columnDefinition = "TEXT")
    private String site;

    @Column(nullable = false)
    private LocalDate date;  // Novo campo para a data específica

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_segment_id")
    private TripSegment tripSegment;
} 