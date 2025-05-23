package org.example.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "trip_users")
@IdClass(TripUser.TripUserId.class)
public class TripUser {
    @Id
    @Column(name = "trip_id")
    private Long tripId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "permission_level", nullable = false, length = 20)
    @Builder.Default
    private String permissionLevel = "viewer";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", insertable = false, updatable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TripUserId implements java.io.Serializable {
        private Long tripId;
        private Long userId;
    }
} 