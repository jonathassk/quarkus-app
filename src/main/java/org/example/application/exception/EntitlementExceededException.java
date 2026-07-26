package org.example.application.exception;

/**
 * Limite do plano atingido — o cliente deve fazer upgrade.
 * Mapeado para HTTP 402 com código {@code ENTITLEMENT_EXCEEDED}.
 */
public class EntitlementExceededException extends RuntimeException {

    private final String feature;
    private final String planType;
    private final long limit;
    private final long used;

    public EntitlementExceededException(String feature, String planType, long limit, long used, String message) {
        super(message);
        this.feature = feature;
        this.planType = planType;
        this.limit = limit;
        this.used = used;
    }

    public String getFeature() {
        return feature;
    }

    public String getPlanType() {
        return planType;
    }

    public long getLimit() {
        return limit;
    }

    public long getUsed() {
        return used;
    }

    public String getCode() {
        return "ENTITLEMENT_EXCEEDED";
    }
}
