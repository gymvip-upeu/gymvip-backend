package com.gymvip.controller;

import com.gymvip.dto.request.CreateStaffAccountRequest;
import com.gymvip.dto.request.UpdateStaffRoleRequest;
import com.gymvip.dto.request.UpdateStaffStatusRequest;
import com.gymvip.dto.response.StaffAccountResponse;
import com.gymvip.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@PreAuthorize("hasRole('ADMIN')")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ResponseEntity<List<StaffAccountResponse>> listAccounts() {
        return ResponseEntity.ok(accountService.listStaffAccounts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<StaffAccountResponse> getAccount(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.getById(id));
    }

    @PostMapping
    public ResponseEntity<StaffAccountResponse> createStaffAccount(
            @Valid @RequestBody CreateStaffAccountRequest request,
            Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "admin_sistema";
        StaffAccountResponse created = accountService.createStaffAccount(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<StaffAccountResponse> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStaffRoleRequest request,
            Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "admin_sistema";
        StaffAccountResponse updated = accountService.updateRole(id, request, actor);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<StaffAccountResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStaffStatusRequest request,
            Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "admin_sistema";
        StaffAccountResponse updated = accountService.updateStatus(id, request, actor);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/revoke-sessions")
    public ResponseEntity<Void> revokeSessions(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "admin_sistema";
        accountService.revokeSessions(id, actor);
        return ResponseEntity.noContent().build();
    }
}
