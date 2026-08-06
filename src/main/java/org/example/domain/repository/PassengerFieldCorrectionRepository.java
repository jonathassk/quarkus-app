package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.PassengerFieldCorrection;
import org.example.domain.enums.PassengerCorrectionStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PassengerFieldCorrectionRepository
        implements PanacheRepositoryBase<PassengerFieldCorrection, UUID> {

    public List<PassengerFieldCorrection> findOpenByPassengerId(UUID passengerId) {
        return list(
                "passenger.id = ?1 AND status = ?2 ORDER BY requestedAt ASC",
                passengerId,
                PassengerCorrectionStatus.OPEN);
    }

    public List<PassengerFieldCorrection> findByPassengerId(UUID passengerId) {
        return list("passenger.id = ?1 ORDER BY requestedAt DESC", passengerId);
    }

    public Optional<PassengerFieldCorrection> findOpenByIdAndPassenger(
            UUID correctionId, UUID passengerId) {
        return find(
                        "id = ?1 AND passenger.id = ?2 AND status = ?3",
                        correctionId,
                        passengerId,
                        PassengerCorrectionStatus.OPEN)
                .firstResultOptional();
    }

    public long countOpenByPassengerId(UUID passengerId) {
        return count(
                "passenger.id = ?1 AND status = ?2",
                passengerId,
                PassengerCorrectionStatus.OPEN);
    }
}
