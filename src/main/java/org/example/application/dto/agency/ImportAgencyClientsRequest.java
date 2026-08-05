package org.example.application.dto.agency;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportAgencyClientsRequest {
    /** Linhas a importar (CSV parseado no cliente ou JSON). */
    private List<ImportRow> clients;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportRow {
        private String name;
        private String email;
        private String phone;
        private String notes;
        private String tags;
    }
}
