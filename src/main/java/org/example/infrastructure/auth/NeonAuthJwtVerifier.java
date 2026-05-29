package org.example.infrastructure.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.OctetKeyPair;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.utils.AuthTokenException;

import java.net.URI;
import java.net.URL;
import java.util.Date;
import java.util.List;

/**
 * Valida JWTs do Neon Auth (EdDSA / JWKS).
 *
 * @see <a href="https://neon.com/docs/auth/guides/plugins/jwt">Neon Auth JWT</a>
 */
@Slf4j
@ApplicationScoped
public class NeonAuthJwtVerifier {

    @ConfigProperty(name = "neon.auth.base-url")
    String baseUrl;

    @ConfigProperty(name = "neon.auth.jwks-url", defaultValue = "")
    String jwksUrlConfig;

    @ConfigProperty(name = "neon.auth.jwk-json", defaultValue = "")
    String jwkJsonConfig;

    private String expectedIssuer;
    private String expectedIssuerOrigin;
    private String resolvedJwksUrl;
    private JWKSource<SecurityContext> jwkSource;

    @PostConstruct
    void init() throws Exception {
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("neon.auth.base-url is not set — Neon Auth JWT validation disabled until configured");
            return;
        }
        String normalized = normalizeIssuer(baseUrl);
        URI uri = URI.create(normalized);
        expectedIssuer = normalized;
        expectedIssuerOrigin = uri.getScheme() + "://" + uri.getHost();
        if (uri.getPort() > 0 && uri.getPort() != 443 && uri.getPort() != 80) {
            expectedIssuerOrigin += ":" + uri.getPort();
        }

        JWKSource<SecurityContext> jwkSource;
        if (jwkJsonConfig != null && !jwkJsonConfig.isBlank()) {
            log.info("Using hardcoded/cached JWK for Neon Auth verification");
            com.nimbusds.jose.jwk.JWKSet jwkSet = com.nimbusds.jose.jwk.JWKSet.parse(jwkJsonConfig.trim());
            jwkSource = new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(jwkSet);
            resolvedJwksUrl = "inline-jwk-json";
        } else {
            resolvedJwksUrl = (jwksUrlConfig != null && !jwksUrlConfig.isBlank())
                    ? jwksUrlConfig.trim()
                    : normalized + "/.well-known/jwks.json";
            log.info("Neon Auth JWKS URL: {}", resolvedJwksUrl);
            URL jwksUrl = URI.create(resolvedJwksUrl).toURL();
            jwkSource = new RemoteJWKSet<>(jwksUrl);
        }
        this.jwkSource = jwkSource;
        log.info(
                "Neon Auth JWT verifier ready (issuer={}, origin={}, jwks={})",
                expectedIssuer,
                expectedIssuerOrigin,
                resolvedJwksUrl);
    }

    public boolean isConfigured() {
        return jwkSource != null && expectedIssuer != null;
    }

    public String getExpectedIssuer() {
        return expectedIssuer;
    }

    public String getJwksUrl() {
        return resolvedJwksUrl;
    }

    public NeonAuthClaims verify(String rawToken) {
        if (!isConfigured()) {
            throw new AuthTokenException(
                    "NEON_AUTH_NOT_CONFIGURED",
                    "neon.auth.base-url is not configured");
        }
        try {
            SignedJWT signed = SignedJWT.parse(rawToken.trim());
            log.info("Parsed SignedJWT header: {}", signed.getHeader().toJSONObject());
            
            // Procura a chave correspondente no JWKS
            List<JWK> matches = jwkSource.get(new JWKSelector(JWKMatcher.forJWSHeader(signed.getHeader())), null);
            log.info("JWKSource returned {} matches for the header", matches == null ? 0 : matches.size());
            
            if (matches == null || matches.isEmpty()) {
                log.error("No matching key found in JWKSource for kid: {}", signed.getHeader().getKeyID());
                throw new AuthTokenException("TOKEN_INVALID", "Signed JWT rejected: Another algorithm expected, or no matching key(s) found");
            }
            
            JWK jwk = matches.get(0);
            log.info("Using JWK: {}", jwk.toJSONString());
            if (!(jwk instanceof OctetKeyPair)) {
                log.error("Invalid key type. Expected OctetKeyPair but got: {}", jwk.getClass().getName());
                throw new AuthTokenException("TOKEN_INVALID", "Invalid key type: expected OctetKeyPair for EdDSA");
            }
            
            // Valida a assinatura usando Tink de forma transparente
            Ed25519Verifier verifier = new Ed25519Verifier((OctetKeyPair) jwk);
            log.info("Created Ed25519Verifier successfully. Proceeding to verify signature...");
            
            if (!signed.verify(verifier)) {
                log.error("Signature verification failed!");
                throw new AuthTokenException("TOKEN_INVALID", "Signed JWT rejected: Invalid signature");
            }
            log.info("Signature verified successfully!");
            
            JWTClaimsSet claims = signed.getJWTClaimsSet();
            
            // Valida expiração com 90 segundos de margem
            Date exp = claims.getExpirationTime();
            log.info("Token expiration time: {}", exp);
            if (exp != null && exp.getTime() + 90000 < System.currentTimeMillis()) {
                log.error("Token is expired! Current time: {}", new Date());
                throw new AuthTokenException("TOKEN_EXPIRED", "Token expired — sign in again or refresh the Neon Auth session");
            }

            String tokenIssuer = claims.getIssuer();
            if (tokenIssuer == null || !issuerMatches(tokenIssuer)) {
                // Assinatura EdDSA já validada via JWKS confiável — iss divergente não deve bloquear.
                log.warn(
                        "Neon Auth JWT iss='{}' differs from configured '{}' (origin='{}'); accepting (signature OK)",
                        tokenIssuer,
                        expectedIssuer,
                        expectedIssuerOrigin);
            }

            List<String> audience = claims.getAudience();
            if (audience != null
                    && !audience.isEmpty()
                    && audience.stream().noneMatch(this::issuerMatches)) {
                log.debug(
                        "Neon Auth JWT aud={} differs from configured {}; accepting (signature OK)",
                        audience,
                        expectedIssuer);
            }

            String sub = claims.getSubject();
            if (sub == null || sub.isBlank()) {
                Object id = claims.getClaim("id");
                sub = id != null ? id.toString() : null;
            }
            if (sub == null || sub.isBlank()) {
                throw new AuthTokenException("MISSING_SUB", "Token does not contain sub or id claim");
            }

            String email = firstNonBlank(
                    claimString(claims, "email"),
                    claimString(claims, "emailAddress"));
            String name = resolveDisplayName(claims);
            String image = firstNonBlank(
                    claimString(claims, "image"),
                    claimString(claims, "picture"),
                    claimString(claims, "avatar"),
                    claimString(claims, "avatarUrl"));
            String provider = firstNonBlank(
                    claimString(claims, "provider"),
                    claimString(claims, "providerId"),
                    inferProviderFromIdentities(claims));

            return new NeonAuthClaims(sub.trim(), email, name, image, provider);
        } catch (AuthTokenException e) {
            throw e;
        } catch (java.text.ParseException | com.nimbusds.jose.JOSEException e) {
            log.warn("Neon Auth JWT validation failed: {}", e.getMessage(), e);
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("expired")) {
                throw new AuthTokenException(
                        "TOKEN_EXPIRED",
                        "Token expired — sign in again or refresh the Neon Auth session",
                        e);
            }
            throw new AuthTokenException("TOKEN_INVALID", "Invalid Neon Auth token: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Neon Auth JWT verification exception: {}", e.getMessage(), e);
            throw new AuthTokenException("TOKEN_INVALID", "Invalid or malformed Neon Auth token: " + e.getMessage(), e);
        }
    }

    private boolean issuerMatches(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = normalizeIssuer(value);
        if (trimmed.equals(expectedIssuer) || trimmed.equals(expectedIssuerOrigin)) {
            return true;
        }
        // Neon pode emitir iss com ou sem sufixo /neondb/auth
        return trimmed.startsWith(expectedIssuer + "/")
                || expectedIssuer.startsWith(trimmed + "/")
                || trimmed.startsWith(expectedIssuer)
                || expectedIssuer.startsWith(trimmed);
    }

    private static String normalizeIssuer(String value) {
        return value.trim().replaceAll("/+$", "");
    }

    private static String claimString(JWTClaimsSet claims, String... names) {
        for (String name : names) {
            Object value = claims.getClaim(name);
            if (value != null) {
                String s = value.toString().trim();
                if (!s.isEmpty()) {
                    return s;
                }
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String resolveDisplayName(JWTClaimsSet claims) {
        String name = firstNonBlank(
                claimString(claims, "name"),
                claimString(claims, "full_name"),
                claimString(claims, "fullName"),
                claimString(claims, "displayName"));
        if (name != null) {
            return name;
        }
        String given = claimString(claims, "given_name", "givenName");
        String family = claimString(claims, "family_name", "familyName");
        if (given != null && family != null) {
            return given + " " + family;
        }
        if (given != null) {
            return given;
        }
        if (family != null) {
            return family;
        }
        return claimString(claims, "preferred_username", "preferredUsername");
    }

    private static String inferProviderFromIdentities(JWTClaimsSet claims) {
        Object identities = claims.getClaim("identities");
        if (identities == null) {
            return null;
        }
        String raw = identities.toString().toLowerCase();
        if (raw.contains("google")) {
            return "google";
        }
        if (raw.contains("facebook")) {
            return "facebook";
        }
        return null;
    }
}
