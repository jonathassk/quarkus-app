package org.example.application.dto.trip.request;

import lombok.*;
import org.example.domain.enums.TripStatus;

/**
 * PATCH parcial de viagem — hoje usado principalmente para status em viagens sem datas fixas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatchTripRequestDTO {
    private String name;
    private String description;
    /** Passagens já compradas (texto livre). String vazia limpa o campo. */
    private String flightDetails;
    /** Hospedagem já reservada (texto livre). String vazia limpa o campo. */
    private String hotelDetails;
    /**
     * Aceita valores do front ({@code planning}/{@code confirmed}/{@code completed})
     * ou do enum backend ({@code PLANNING}/{@code ONGOING}/{@code COMPLETED}).
     */
    private String status;
}
