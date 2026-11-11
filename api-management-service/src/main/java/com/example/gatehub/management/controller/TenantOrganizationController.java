package com.example.gatehub.management.controller;

import com.example.gatehub.management.dto.requestDto.TenantOrganizationRequest;
import com.example.gatehub.management.dto.responseDto.OrganizationResponse;
import com.example.gatehub.management.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenant/organization")
public class TenantOrganizationController {

    private OrganizationService organizationService;

    public TenantOrganizationController(OrganizationService organizationService){
        this.organizationService = organizationService;
    }

    // get organisation
    @GetMapping
    public ResponseEntity<OrganizationResponse> getOwnOrganization() {
        return ResponseEntity.ok(organizationService.getOwnOrganization());
    }

    // update name of organisation
    @PutMapping
    public ResponseEntity<OrganizationResponse> updateOwnOrganization(
            @Valid @RequestBody TenantOrganizationRequest request) {
        return ResponseEntity.ok(organizationService.updateOwnOrganization(request));
    }

}
