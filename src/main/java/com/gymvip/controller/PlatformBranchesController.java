package com.gymvip.controller;

import com.gymvip.enterprise.BranchRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/organizations/{organizationId}/branches")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformBranchesController {
    private final BranchRepository branches;
    public PlatformBranchesController(BranchRepository branches) { this.branches = branches; }

    public record Option(UUID id, String name) {}

    @GetMapping
    public List<Option> list(@PathVariable UUID organizationId) {
        return branches.findByOrganizationIdOrderByNameAsc(organizationId).stream()
                .filter(branch -> "ACTIVO".equals(branch.getStatus()))
                .map(branch -> new Option(branch.getId(), branch.getName())).toList();
    }
}
