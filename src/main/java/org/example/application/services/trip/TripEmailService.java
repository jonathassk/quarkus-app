package org.example.application.services.trip;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.trip.request.SendTripEmailRequestDTO;
import org.example.application.dto.trip.response.SendTripEmailResponseDTO;
import org.example.domain.entity.Trip;
import org.example.domain.entity.User;
import org.example.domain.enums.UserPermissionLevel;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;
import org.example.application.services.TripCollaborationService;
import org.example.infrastructure.email.EmailWorkerInvoker;

import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class TripEmailService {

    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final TripCollaborationService tripCollaborationService;
    private final EmailWorkerInvoker emailWorkerInvoker;

    @Transactional
    public SendTripEmailResponseDTO sendTripEmail(UUID tripId, UUID userId, SendTripEmailRequestDTO request) {
        Trip trip = tripRepository.findById(tripId);
        if (trip == null) {
            throw new IllegalArgumentException("TRIP_NOT_FOUND");
        }

        UserPermissionLevel level = tripCollaborationService.resolvePermission(trip, userId);
        if (level == null) {
            throw new SecurityException("FORBIDDEN");
        }

        User user = userRepository.findById(userId);
        if (user == null) {
            throw new SecurityException("FORBIDDEN");
        }

        if (request == null || request.getToEmail() == null || request.getToEmail().isBlank()) {
            throw new IllegalArgumentException("INVALID_EMAIL");
        }
        String to = request.getToEmail().trim();
        if (!EMAIL.matcher(to).matches()) {
            throw new IllegalArgumentException("INVALID_EMAIL");
        }

        String subject = blankToDefault(request.getSubject(), "Roteiro: " + nullToEmpty(trip.getName()));
        String textBody = blankToDefault(request.getTextBody(), buildFallbackText(trip, user));
        String htmlBody = request.getHtmlBody() != null ? request.getHtmlBody() : "";

        boolean queued = emailWorkerInvoker.enqueueDirectEmail(to, subject, textBody, htmlBody);
        if (queued) {
            return SendTripEmailResponseDTO.builder()
                    .status("queued")
                    .message("Email queued for delivery")
                    .build();
        }

        return SendTripEmailResponseDTO.builder()
                .status("mailto_fallback")
                .message("Email worker unavailable; client should use mailto")
                .build();
    }

    private static String buildFallbackText(Trip trip, User user) {
        StringBuilder sb = new StringBuilder();
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            sb.append(user.getFullName()).append(" compartilhou um roteiro com você.\n\n");
        }
        sb.append(nullToEmpty(trip.getName())).append("\n");
        if (trip.getDescription() != null && !trip.getDescription().isBlank()) {
            sb.append(trip.getDescription()).append("\n");
        }
        sb.append("\n— Enviado pelo Baggagi");
        return sb.toString();
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
