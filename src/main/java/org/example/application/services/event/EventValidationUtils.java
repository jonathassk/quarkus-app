package org.example.application.services.event;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.util.regex.Pattern;

@ApplicationScoped
public class EventValidationUtils {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");

    @ConfigProperty(name = "r2.public-url-prefix", defaultValue = "")
    String r2PublicUrlPrefix;

    public String sanitizeText(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        String cleaned = HTML_TAG.matcher(text).replaceAll("").trim();
        if (cleaned.length() > maxLength) {
            throw org.example.application.exception.event.EventException.validation(
                    "Text exceeds maximum length of " + maxLength);
        }
        return cleaned;
    }

    public void validateImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        URI uri;
        try {
            uri = URI.create(imageUrl.trim());
        } catch (IllegalArgumentException e) {
            throw org.example.application.exception.event.EventException.validation("Invalid image URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw org.example.application.exception.event.EventException.validation("Image URL must use HTTPS");
        }
        if (r2PublicUrlPrefix != null && !r2PublicUrlPrefix.isBlank()) {
            String prefix = r2PublicUrlPrefix.endsWith("/") ? r2PublicUrlPrefix : r2PublicUrlPrefix + "/";
            if (!imageUrl.startsWith(prefix) && !imageUrl.startsWith(r2PublicUrlPrefix)) {
                throw org.example.application.exception.event.EventException.validation(
                        "Image URL must be from allowed storage domain");
            }
        }
    }
}
