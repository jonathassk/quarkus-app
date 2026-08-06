package org.example.application.services.ops;

import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.OperationalService;
import org.example.domain.enums.OperationStatus;
import org.example.domain.enums.OperationalServiceStatus;
import org.example.domain.enums.ProposalStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * Calcula o status geral da viagem a partir dos serviços operacionais.
 */
@ApplicationScoped
public class OperationStatusRollup {

    public OperationStatus calculate(
            List<OperationalService> services,
            ProposalStatus proposalStatus,
            LocalDate startDate,
            LocalDate endDate) {

        if (proposalStatus == ProposalStatus.CANCELLED) {
            return OperationStatus.CANCELLED;
        }

        LocalDate today = LocalDate.now();
        if (endDate != null && today.isAfter(endDate)) {
            return OperationStatus.COMPLETED;
        }
        if (startDate != null && endDate != null
                && !today.isBefore(startDate) && !today.isAfter(endDate)) {
            return OperationStatus.IN_TRIP;
        }

        List<OperationalService> active = services == null ? List.of() : services.stream()
                .filter(s -> s.getStatus() != null && s.getStatus().isActive())
                .toList();

        if (active.isEmpty()) {
            return OperationStatus.PREPARING_RESERVATIONS;
        }

        long settled = active.stream()
                .filter(s -> s.getStatus().isSettled())
                .count();
        long inFlight = active.stream()
                .filter(s -> {
                    OperationalServiceStatus st = s.getStatus();
                    return st == OperationalServiceStatus.REQUESTED
                            || st == OperationalServiceStatus.WAITING
                            || st == OperationalServiceStatus.PRE_RESERVED
                            || st == OperationalServiceStatus.CHANGE_PENDING;
                })
                .count();

        if (settled == active.size()) {
            return OperationStatus.READY_TO_TRAVEL;
        }
        if (settled > 0) {
            return OperationStatus.PARTIALLY_CONFIRMED;
        }
        if (inFlight > 0) {
            return OperationStatus.RESERVATIONS_IN_PROGRESS;
        }
        return OperationStatus.PREPARING_RESERVATIONS;
    }
}
