package org.example.infrastructure.http;

import jakarta.ws.rs.container.ContainerRequestContext;

/**
 * Resolve o IP do cliente a partir de headers de proxy (API Gateway / CloudFront).
 */
public final class ClientIp {

    private ClientIp() {}

    public static String from(ContainerRequestContext ctx) {
        String forwarded = ctx.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = ctx.getHeaderString("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return "unknown";
    }
}
