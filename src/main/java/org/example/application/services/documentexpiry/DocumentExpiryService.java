package org.example.application.services.documentexpiry;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.application.dto.documentexpiry.DocumentExpiryDTO;
import org.example.application.services.notification.NotificationService;
import org.example.domain.entity.DocumentExpiry;
import org.example.domain.entity.User;
import org.example.domain.enums.DocumentExpiryKind;
import org.example.domain.enums.NotificationKind;
import org.example.domain.repository.DocumentExpiryRepository;
import org.example.domain.repository.NotificationRepository;
import org.example.domain.repository.UserRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
@RequiredArgsConstructor
public class DocumentExpiryService {

    /** Janelas alinhadas ao email-worker (document_expiry_reminders). */
    private static final Set<Long> EXPIRY_WINDOWS_DAYS = Set.of(180L, 90L, 30L, 7L, 1L);

    private final DocumentExpiryRepository documentExpiryRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    @Transactional
    public List<DocumentExpiryDTO> list(UUID userId) {
        return documentExpiryRepository.findByUserId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Cria/atualiza um documento. Para tipos fixos (PASSPORT/VISA/INTERNATIONAL_LICENSE)
     * reaproveita o registro existente do usuário (upsert); para CUSTOM sempre cria um novo.
     */
    @Transactional
    public DocumentExpiryDTO upsert(
            UUID userId, DocumentExpiryKind kind, String name, LocalDate expiryDate, Boolean alertEnabled) {
        DocumentExpiry doc;
        if (kind != DocumentExpiryKind.CUSTOM) {
            doc = documentExpiryRepository.findByUserIdAndKind(userId, kind).orElseGet(() -> newDocument(userId, kind));
        } else {
            doc = newDocument(userId, DocumentExpiryKind.CUSTOM);
            doc.setName(name);
        }
        doc.setExpiryDate(expiryDate);
        if (alertEnabled != null) {
            doc.setAlertEnabled(alertEnabled);
        }
        documentExpiryRepository.persist(doc);
        maybeNotifyExpiring(userId, doc);
        return toDto(doc);
    }

    @Transactional
    public Optional<DocumentExpiryDTO> update(UUID userId, UUID id, String name, LocalDate expiryDate, boolean expiryDateProvided, Boolean alertEnabled) {
        return documentExpiryRepository.findByIdAndUserId(id, userId).map(doc -> {
            if (name != null) {
                doc.setName(name);
            }
            if (expiryDateProvided) {
                doc.setExpiryDate(expiryDate);
            }
            if (alertEnabled != null) {
                doc.setAlertEnabled(alertEnabled);
            }
            maybeNotifyExpiring(userId, doc);
            return toDto(doc);
        });
    }

    @Transactional
    public boolean delete(UUID userId, UUID id) {
        Optional<DocumentExpiry> docOpt = documentExpiryRepository.findByIdAndUserId(id, userId);
        if (docOpt.isEmpty()) {
            return false;
        }
        documentExpiryRepository.delete(docOpt.get());
        return true;
    }

    /**
     * In-app quando a validade cai exatamente numa janela de aviso.
     * E-mail fica a cargo do email-worker ({@code document_expiry_reminders}).
     */
    private void maybeNotifyExpiring(UUID userId, DocumentExpiry doc) {
        if (doc == null || !doc.isAlertEnabled() || doc.getExpiryDate() == null || doc.id == null) {
            return;
        }
        long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), doc.getExpiryDate());
        if (!EXPIRY_WINDOWS_DAYS.contains(daysLeft)) {
            return;
        }
        if (notificationRepository.existsUnreadOrRecent(
                userId, "DOCUMENT", doc.id, NotificationKind.DOC_EXPIRING)) {
            return;
        }
        String docName = displayName(doc);
        notificationService.notifyDocumentExpiring(
                userId,
                doc.id,
                docName + " vence em " + daysLeft + " dias",
                "Seu documento \"" + docName + "\" vence em " + daysLeft + " dias.",
                false);
    }

    private static String displayName(DocumentExpiry doc) {
        if (doc.getName() != null && !doc.getName().isBlank()) {
            return doc.getName().trim();
        }
        return switch (doc.getKind()) {
            case PASSPORT -> "Passaporte";
            case VISA -> "Visto";
            case INTERNATIONAL_LICENSE -> "CNH internacional";
            case CUSTOM -> "Documento";
        };
    }

    private DocumentExpiry newDocument(UUID userId, DocumentExpiryKind kind) {
        User user = userRepository.findById(userId);
        return DocumentExpiry.builder().user(user).kind(kind).build();
    }

    private DocumentExpiryDTO toDto(DocumentExpiry doc) {
        return DocumentExpiryDTO.builder()
                .id(doc.id)
                .kind(doc.getKind().name())
                .name(doc.getName())
                .expiryDate(doc.getExpiryDate() != null ? doc.getExpiryDate().toString() : null)
                .alertEnabled(doc.isAlertEnabled())
                .build();
    }
}
