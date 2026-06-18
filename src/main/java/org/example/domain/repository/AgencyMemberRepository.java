package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.Agency;
import org.example.domain.entity.AgencyMember;
import org.example.domain.enums.AgencyRole;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class AgencyMemberRepository implements PanacheRepository<AgencyMember> {

    /**
     * Retorna o vínculo de um usuário com uma agência específica, se existir.
     */
    public Optional<AgencyMember> findByAgencyAndUser(Long agencyId, Long userId) {
        return find("agency.id = :aid AND user.id = :uid",
                io.quarkus.panache.common.Parameters.with("aid", agencyId).and("uid", userId))
                .firstResultOptional();
    }

    /**
     * Retorna todos os membros de uma agência.
     */
    public List<AgencyMember> findAllByAgency(Long agencyId) {
        return list("agency.id", agencyId);
    }

    /**
     * Retorna todas as agências às quais um usuário pertence.
     */
    public List<AgencyMember> findAllByUser(Long userId) {
        return list("user.id", userId);
    }

    /**
     * Verifica se o usuário pertence à agência com o papel mínimo informado.
     */
    public boolean isMemberWithRole(Long agencyId, Long userId, AgencyRole minimumRole) {
        return findByAgencyAndUser(agencyId, userId)
                .map(m -> m.getAgencyRole().getHierarchyLevel() >= minimumRole.getHierarchyLevel())
                .orElse(false);
    }

    /**
     * Retorna a agência de um usuário (caso pertença a exatamente uma), ou a primeira encontrada.
     * Útil para inferir a agência do contexto autenticado.
     */
    public Optional<Agency> findPrimaryAgencyForUser(Long userId) {
        return findAllByUser(userId).stream()
                .findFirst()
                .map(AgencyMember::getAgency);
    }
}
