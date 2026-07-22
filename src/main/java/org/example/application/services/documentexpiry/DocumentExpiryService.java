package org.example.application.services.documentexpiry;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.application.dto.documentexpiry.DocumentExpiryDTO;
import org.example.domain.entity.DocumentExpiry;
import org.example.domain.entity.User;
import org.example.domain.enums.DocumentExpiryKind;
import org.example.domain.repository.DocumentExpiryRepository;
import org.example.domain.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
@RequiredArgsConstructor
public class DocumentExpiryService {

    private final DocumentExpiryRepository documentExpiryRepository;
    private final UserRepository userRepository;

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

    private DocumentExpiry newDocument(UUID userId, DocumentExpiryKind kind) {
        User user = userRepository.findById(userId);
        return DocumentExpiry.builder().user(user).kind(kind).build();
    }

    private DocumentExpiryDTO toDto(DocumentExpiry doc) {
        return DocumentExpiryDTO.builder()
                .id(doc.getId())
                .kind(doc.getKind().name())
                .name(doc.getName())
                .expiryDate(doc.getExpiryDate() != null ? doc.getExpiryDate().toString() : null)
                .alertEnabled(doc.isAlertEnabled())
                .build();
    }
}
