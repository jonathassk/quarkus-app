package org.example.application.dto.entitlement;

import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * Snapshot dos limites efetivos do usuário autenticado.
 * O front renderiza paywall a partir daqui — sem regras duplicadas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntitlementsDTO {
    private UUID workspaceId;
    private String planType;
    private String userType;
    private LimitUsage activeTrips;
    private LimitUsage aiGenerationsMonth;
    private LimitUsage documentBytes;
    private LimitUsage shareLinks;
    private boolean canExportPdf;
    private boolean canUseAi;
    /** Máx. dias corridos por planejamento (FREE = 8; -1 = sem teto). */
    private int maxDaysPerPlan;
    /** Máx. cidades/segmentos por planejamento (FREE = 2; -1 = sem teto). */
    private int maxCitiesPerPlan;
    /** Features desbloqueadas por compra UNITARIO em alguma viagem (resumo). */
    private List<String> tripUnlockKinds;
    private List<SuggestedUpgrade> upgrades;
    /**
     * Modelo de IA autorizado pelo servidor: {@code FLASH} (FREE) ou {@code PRO} (PREMIUM/B2B).
     * O front deve enviar este valor à Lambda — não escolher o modelo no cliente.
     */
    private String aiModelTier;
    /**
     * {@code true} se o usuário ainda pode iniciar o trial de 5 dias
     * (sem assinatura ativa e sem {@code trial_used_at}).
     */
    private boolean trialEligible;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LimitUsage {
        private long used;
        private long limit;
        /** true quando limit &lt; 0 (ilimitado). */
        private boolean unlimited;

        public boolean isExceeded() {
            return !unlimited && used >= limit;
        }

        public boolean canConsume(long additional) {
            return unlimited || used + additional <= limit;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuggestedUpgrade {
        private String paymentType;
        private String label;
        private String description;
    }
}
