package com.example.gatehub.management.service;

import com.example.gatehub.management.dto.requestDto.OrganizationRequest;
import com.example.gatehub.management.dto.requestDto.OrganizationStatusRequest;
import com.example.gatehub.management.dto.requestDto.TenantAdminRequest;
import com.example.gatehub.management.dto.requestDto.TenantOrganizationRequest;
import com.example.gatehub.management.dto.responseDto.OrganizationResponse;
import com.example.gatehub.management.dto.responseDto.TenantAdminResponse;
import com.example.gatehub.management.entity.ManagementUser;
import com.example.gatehub.management.entity.Organization;
import com.example.gatehub.management.entity.OrganizationStatus;
import com.example.gatehub.management.entity.Role;
import com.example.gatehub.management.exception.ApiException;
import com.example.gatehub.management.repository.ManagementUserRepository;
import com.example.gatehub.management.repository.OrganizationRepository;
import com.example.gatehub.management.repository.RoleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final ManagementUserRepository managementUserRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final TenantContext tenantContext;

    public OrganizationService(OrganizationRepository organizationRepository,
                               ManagementUserRepository managementUserRepository,
                               RoleRepository roleRepository, PasswordEncoder passwordEncoder,
                               AuditService auditService, TenantContext tenantContext) {
        this.organizationRepository = organizationRepository;
        this.managementUserRepository = managementUserRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.tenantContext = tenantContext;
    }

    // CRUD on ORGANISATION

    // create organisation
    @Transactional
    public OrganizationResponse create(OrganizationRequest request) {
        long startedAt = System.nanoTime();

        checkUnique(request.getName(), request.getSlug(), null);

        Organization organization = new Organization();
        organization.setName(request.getName());
        organization.setSlug(request.getSlug());
        organization.setStatus(OrganizationStatus.PENDING);
        organization.setCreatedAt(LocalDateTime.now());
        organization.setUpdatedAt(LocalDateTime.now());

        organizationRepository.save(organization);

        auditService.record(organization, "CREATE_ORGANIZATION", "ORGANIZATION",
                organization.getId().toString(), startedAt,
                "Organization created in PENDING status");

        return OrganizationResponse.from(organization);
    }

    // get(read) all organisation
    @Transactional(readOnly = true)
    public List<OrganizationResponse> findAll() {
        List<OrganizationResponse> response = new ArrayList<OrganizationResponse>();

        for(Organization organization :
                organizationRepository.findAllByStatusNot(OrganizationStatus.DELETED)) {
            response.add(OrganizationResponse.from(organization));
        }
        return response;
    }

    // get(read) one organisation
    @Transactional(readOnly = true)
    public OrganizationResponse findById(Long id) {
        return OrganizationResponse.from(getOrganization(id));
    }

    // update organisation
    @Transactional
    public OrganizationResponse update(Long id, OrganizationRequest request) {
        long startedAt = System.nanoTime();
        Organization organization = getOrganization(id);

        checkUnique(request.getName(), request.getSlug(), id);

        organization.setName(request.getName());
        organization.setSlug(request.getSlug());
        organization.setUpdatedAt(LocalDateTime.now());

        auditService.record(organization, "UPDATE_ORGANIZATION",
                "ORGANIZATION", id.toString(), startedAt,
                "Organization name and slug updated");

        return OrganizationResponse.from(organization);
    }

    // change status of organisation
    @Transactional
    public OrganizationResponse changeStatus(Long id, OrganizationStatusRequest request) {

        long startedAt = System.nanoTime();
        Organization organization = getOrganization(id);

        if(request.getStatus() == OrganizationStatus.DELETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Use DELETE to soft-delete an organization");
        }

        organization.setStatus(request.getStatus());
        organization.setUpdatedAt(LocalDateTime.now());

        auditService.record(organization, "CHANGE_ORGANIZATION_STATUS",
                "ORGANIZATION", id.toString(),
                startedAt, "Status changed to " + request.getStatus());

        return OrganizationResponse.from(organization);
    }

    // delete organisation
    @Transactional
    public void delete(Long id) {
        long startedAt = System.nanoTime();

        Organization organization = getOrganization(id);
        organization.setStatus(OrganizationStatus.DELETED);
        organization.setDeletedAt(LocalDateTime.now());
        organization.setUpdatedAt(LocalDateTime.now());

        auditService.record(organization, "DELETE_ORGANIZATION",
                "ORGANIZATION", id.toString(), startedAt,
                "Organization soft-deleted");
    }

    // CRUD on TENANT_ADMIN

    // create tenant_admin
    @Transactional
    public TenantAdminResponse createTenantAdmin(Long organizationId, TenantAdminRequest request) {
        long startedAt = System.nanoTime();
        Organization organization = getOrganization(organizationId);

        if(managementUserRepository.findByOrganizationId(organizationId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This organization already has a tenant administrator");
        }

        if(managementUserRepository.existsByUsername(request.getUsername())) {
            throw new ApiException(HttpStatus.CONFLICT, "Username already exists");
        }

        Role role = roleRepository.findByName("TENANT_ADMIN")
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "TENANT_ADMIN role is missing"));

        ManagementUser user = new ManagementUser();
        user.setOrganization(organization);
        user.setRole(role);
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEnabled(request.isEnabled());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        managementUserRepository.save(user);

        auditService.record(organization, "CREATE_TENANT_ADMIN",
                "MANAGEMENT_USER", user.getId().toString(),
                startedAt, "Tenant administrator created");

        return TenantAdminResponse.from(user);
    }

    // get(read) one tenant_admin
    @Transactional(readOnly = true)
    public TenantAdminResponse getTenantAdmin(Long organizationId) {
        return TenantAdminResponse.from(getTenantAdminEntity(organizationId));
    }

    // update tenant_admin
    @Transactional
    public TenantAdminResponse updateTenantAdmin(Long organizationId, TenantAdminRequest request) {
        long startedAt = System.nanoTime();
        ManagementUser user = getTenantAdminEntity(organizationId);

        if(!user.getUsername().equals(request.getUsername())
                && managementUserRepository.existsByUsername(request.getUsername())) {
            throw new ApiException(HttpStatus.CONFLICT, "Username already exists");
        }

        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEnabled(request.isEnabled());
        user.setUpdatedAt(LocalDateTime.now());

        auditService.record(user.getOrganization(), "UPDATE_TENANT_ADMIN",
                "MANAGEMENT_USER", user.getId().toString(), startedAt,
                "Tenant administrator updated");

        return TenantAdminResponse.from(user);
    }

    // delete tenant_admin
    @Transactional
    public void deleteTenantAdmin(Long organizationId) {
        long startedAt = System.nanoTime();

        ManagementUser user = getTenantAdminEntity(organizationId);

        auditService.record(user.getOrganization(), "DELETE_TENANT_ADMIN",
                "MANAGEMENT_USER", user.getId().toString(), startedAt,
                "Tenant administrator deleted");

        managementUserRepository.delete(user);
    }

    private void checkUnique(String name, String slug, Long currentId) {
        if(organizationRepository.existsByNameIgnoreCase(name)) {
            Organization current = currentId == null ?
                    null : organizationRepository.findById(currentId).orElse(null);

            if(current == null || !current.getName().equalsIgnoreCase(name)) {
                throw new ApiException(HttpStatus.CONFLICT, "Organization name already exists");
            }
        }

        if(organizationRepository.existsBySlugIgnoreCase(slug)) {
            Organization current = currentId == null ?
                    null : organizationRepository.findById(currentId).orElse(null);

            if(current == null || !current.getSlug().equalsIgnoreCase(slug)) {
                throw new ApiException(HttpStatus.CONFLICT, "Organization slug already exists");
            }
        }
    }

    private Organization getOrganization(Long id) {
        return organizationRepository.findByIdAndStatusNot(id, OrganizationStatus.DELETED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Organization not found"));
    }

    private ManagementUser getTenantAdminEntity(Long organizationId) {
        getOrganization(organizationId);
        return managementUserRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tenant administrator not found"));
    }

    // methods for tenant admin
    @Transactional(readOnly = true)
    public OrganizationResponse getOwnOrganization() {
        return OrganizationResponse.from(tenantContext.getOrganization());
    }

    @Transactional
    public OrganizationResponse updateOwnOrganization(TenantOrganizationRequest request) {
        long startedAt = System.nanoTime();

        Organization organization = tenantContext.getOrganization();

        if(!organization.getName().equalsIgnoreCase(request.getName())
                && organizationRepository.existsByNameIgnoreCase(request.getName())) {
            throw new ApiException(HttpStatus.CONFLICT, "Organization name already exists");
        }

        organization.setName(request.getName());
        organization.setUpdatedAt(LocalDateTime.now());

        auditService.record(organization, "UPDATE_OWN_ORGANIZATION",
                "ORGANIZATION", organization.getId().toString(), startedAt,
                "Tenant updated its organization name");

        return OrganizationResponse.from(organization);
    }
}
