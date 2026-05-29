package org.example.infrastructure.auth;

/**
 * Claims relevantes de um JWT emitido pelo Neon Auth (Better Auth), incluindo Google OAuth.
 */
public record NeonAuthClaims(
        String subject,
        String email,
        String name,
        String image,
        String provider) {

    public String authUserId() {
        return subject;
    }

    /** {@code google}, {@code credential}, {@code neon}, etc. */
    public String resolvedProvider() {
        if (provider != null && !provider.isBlank()) {
            return provider.trim().toLowerCase();
        }
        if (image != null && image.contains("googleusercontent.com")) {
            return "google";
        }
        return "neon";
    }
}
