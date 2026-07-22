package org.example.domain.repository;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.Agency;
import org.example.domain.entity.AgencyMember;
import org.example.domain.enums.AgencyRole;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class AgencyMemberRepository implements PanacheRepositoryBase<AgencyMember, UUID> {

    /**
     * Retorna o vínculo de um usuário com uma agência específica, se existir.
     */
    public Optional<AgencyMember> findByAgencyAndUser(UUID agencyId, UUID userId) {
        return find("agency.id = :aid AND user.id = :uid",
                io.quarkus.panache.common.Parameters.with("aid", agencyId).and("uid", userId))
                .firstResultOptional();
    }

    /**
     * Retorna todos os membros de uma agência.
     */
    public List<AgencyMember> findAllByAgency(UUID agencyId) {
        return list("agency.id", agencyId);
    }

    /**
     * Retorna todas as agências às quais um usuário pertence.
     */
    public List<AgencyMember> findAllByUser(UUID userId) {
        return list("user.id", userId);
    }

    /**
     * Verifica se o usuário pertence à agência com o papel mínimo informado.
     */
    public boolean isMemberWithRole(UUID agencyId, UUID userId, AgencyRole minimumRole) {
        return findByAgencyAndUser(agencyId, userId)
                .map(m -> m.getAgencyRole().getHierarchyLevel() >= minimumRole.getHierarchyLevel())
                .orElse(false);
    }

    /**
     * Retorna a agência de um usuário (caso pertença a exatamente uma), ou a primeira encontrada.
     * Útil para inferir a agência do contexto autenticado.
     */
    public Optional<Agency> findPrimaryAgencyForUser(UUID userId) {
        return findAllByUser(userId).stream()
                .findFirst()
                .map(AgencyMember::getAgency);
    }
}
