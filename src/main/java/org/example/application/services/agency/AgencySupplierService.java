package org.example.application.services.agency;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.example.application.dto.ops.AgencySupplierDTO;
import org.example.application.dto.ops.UpsertAgencySupplierRequest;
import org.example.domain.entity.AgencyMember;
import org.example.domain.entity.AgencySupplier;
import org.example.domain.enums.SupplierCategory;
import org.example.domain.repository.AgencySupplierRepository;
import org.example.domain.repository.OperationalServiceRepository;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AgencySupplierService {

    @Inject AgencyService agencyService;
    @Inject AgencySupplierRepository supplierRepository;
    @Inject OperationalServiceRepository serviceRepository;

    public List<AgencySupplierDTO> listSuppliers(UUID agencyUserId) {
        AgencyMember member = agencyService.requireMembershipOrThrow(agencyUserId);
        return supplierRepository.findByAgencyId(member.getAgency().id).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public AgencySupplierDTO upsertSupplier(UUID agencyUserId, UUID supplierId, UpsertAgencySupplierRequest request) {
        AgencyMember member = agencyService.requireMembershipOrThrow(agencyUserId);
        if (request == null) {
            throw new BadRequestException("request is required");
        }
        if (supplierId == null) {
            if (request.getName() == null || request.getName().isBlank()) {
                throw new BadRequestException("name is required");
            }
            String name = request.getName().trim();
            supplierRepository.findByAgencyAndNameIgnoreCase(member.getAgency().id, name)
                    .ifPresent(existing -> {
                        throw new BadRequestException("Supplier with this name already exists");
                    });
            AgencySupplier created = AgencySupplier.builder()
                    .agency(member.getAgency())
                    .name(name)
                    .category(request.getCategory() != null ? request.getCategory() : SupplierCategory.OTHER)
                    .contactName(blankToNull(request.getContactName()))
                    .email(blankToNull(request.getEmail()))
                    .whatsapp(blankToNull(request.getWhatsapp()))
                    .website(blankToNull(request.getWebsite()))
                    .currencies(blankToNull(request.getCurrencies()))
                    .notes(blankToNull(request.getNotes()))
                    .defaultPolicy(blankToNull(request.getDefaultPolicy()))
                    .build();
            supplierRepository.persist(created);
            return toDto(created);
        }

        AgencySupplier supplier = requireSupplier(member.getAgency().id, supplierId);
        if (request.getName() != null && !request.getName().isBlank()) {
            String name = request.getName().trim();
            supplierRepository.findByAgencyAndNameIgnoreCase(member.getAgency().id, name)
                    .ifPresent(other -> {
                        if (!other.id.equals(supplier.id)) {
                            throw new BadRequestException("Supplier with this name already exists");
                        }
                    });
            supplier.setName(name);
        }
        if (request.getCategory() != null) supplier.setCategory(request.getCategory());
        if (request.getContactName() != null) supplier.setContactName(blankToNull(request.getContactName()));
        if (request.getEmail() != null) supplier.setEmail(blankToNull(request.getEmail()));
        if (request.getWhatsapp() != null) supplier.setWhatsapp(blankToNull(request.getWhatsapp()));
        if (request.getWebsite() != null) supplier.setWebsite(blankToNull(request.getWebsite()));
        if (request.getCurrencies() != null) supplier.setCurrencies(blankToNull(request.getCurrencies()));
        if (request.getNotes() != null) supplier.setNotes(blankToNull(request.getNotes()));
        if (request.getDefaultPolicy() != null) supplier.setDefaultPolicy(blankToNull(request.getDefaultPolicy()));
        supplierRepository.persist(supplier);
        return toDto(supplier);
    }

    private AgencySupplier requireSupplier(UUID agencyId, UUID supplierId) {
        AgencySupplier supplier = supplierRepository.findById(supplierId);
        if (supplier == null || !supplier.getAgency().id.equals(agencyId)) {
            throw new NotFoundException("Supplier not found");
        }
        return supplier;
    }

    private AgencySupplierDTO toDto(AgencySupplier supplier) {
        long servicesCount = serviceRepository.count("supplier.id", supplier.id);
        return AgencySupplierDTO.builder()
                .id(supplier.id)
                .name(supplier.getName())
                .category(supplier.getCategory())
                .contactName(supplier.getContactName())
                .email(supplier.getEmail())
                .whatsapp(supplier.getWhatsapp())
                .website(supplier.getWebsite())
                .currencies(supplier.getCurrencies())
                .notes(supplier.getNotes())
                .defaultPolicy(supplier.getDefaultPolicy())
                .servicesCount(servicesCount)
                .build();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
