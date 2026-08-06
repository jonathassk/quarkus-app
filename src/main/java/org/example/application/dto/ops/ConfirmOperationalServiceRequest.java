package org.example.application.dto.ops;

import lombok.Data;

import java.util.Map;

@Data
public class ConfirmOperationalServiceRequest {
    private String locator;
    private String ticketNumber;
    private Long confirmedCostMinor;
    private String currency;
    private String cancellationPolicy;
    private Map<String, Object> publicInfo;
    private String internalNotes;
    private Boolean publish;
    /** Aceitar diferença de custo internamente (registra no audit). */
    private Boolean acceptCostDivergence;
    /** Preferir ISSUED quando o tipo permitir (voo/seguro). */
    private Boolean markIssued;
}
