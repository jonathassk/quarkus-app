package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "activities")
public class Activity extends PanacheEntity {

    @Column(length = 255)
    private String name;

    @Column(name = "activity_type", length = 50)
    private String activityType;

    @Column(length = 255)
    private String address;

    /** Latitude WGS84 (ex.: -23.5505) — integração com mapas */
    @Column(name = "latitude")
    private Double latitude;

    /** Longitude WGS84 (ex.: -46.6333) — integração com mapas */
    @Column(name = "longitude")
    private Double longitude;

    @Column(precision = 10, scale = 2)
    private BigDecimal cost;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "day_number", nullable = false)
    @Builder.Default
    private int dayNumber = 1;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(length = 512)
    private String site;

    private LocalDate date;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "segment_id")
    private TripSegment segment;
} 