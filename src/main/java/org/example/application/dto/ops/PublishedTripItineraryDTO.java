package org.example.application.dto.ops;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.domain.enums.OperationalServiceType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishedTripItineraryDTO {
    private UUID tripId;
    private String tripName;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<PublishedServiceDTO> services;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublishedServiceDTO {
        private UUID id;
        private OperationalServiceType serviceType;
        private String name;
        private String subtitle;
        private String locator;
        private String ticketNumber;
        private Map<String, Object> publicInfo;
        private LocalDate startDate;
        private LocalDate endDate;
        private List<OperationalDocumentDTO> documents;
    }
}
