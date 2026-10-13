package com.gymvip.identity.api;

import com.gymvip.identity.application.AccountManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
public class AccountController {

    private final AccountManagementService accountManagementService;

    public AccountController(AccountManagementService accountManagementService) {
        this.accountManagementService = accountManagementService;
    }

    public record CreateStaffAccountRequest(
            @NotBlank(message = "El correo es obligatorio")
            @Email(message = "El formato de correo no es válido")
            String email,

            @NotBlank(message = "La contraseña es obligatoria")
            @Size(min = 8, message = "La contraseña debe tener mínimo 8 caracteres")
            String password,

            @NotBlank(message = "El rol es obligatorio (ADMIN, RECEPCION)")
            String role
    ) {}

    public record UpdateRoleRequest(
            @NotBlank(message = "El rol es obligatorio (ADMIN, RECEPCION)")
            String role
    ) {}

    public record UpdateStatusRequest(
            @NotBlank(message = "El estado es obligatorio (ACTIVO, INACTIVO)")
            String status
    ) {}

    @GetMapping
    public ResponseEntity<List<AccountManagementService.StaffAccountResponse>> listAccounts() {
        return ResponseEntity.ok(accountManagementService.listStaffAccounts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountManagementService.StaffAccountResponse> getAccount(@PathVariable UUID id) {
        return ResponseEntity.ok(accountManagementService.getById(id));
    }

    @PostMapping
    public ResponseEntity<AccountManagementService.StaffAccountResponse> createStaffAccount(
            @Valid @RequestBody CreateStaffAccountRequest request,
            Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "admin_sistema";
        AccountManagementService.StaffAccountResponse created = accountManagementService.createStaffAccount(
                request.email(),
                request.password(),
                request.role(),
                actor
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<AccountManagementService.StaffAccountResponse> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest request,
            Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "admin_sistema";
        AccountManagementService.StaffAccountResponse updated = accountManagementService.updateRole(
                id,
                request.role(),
                actor
        );
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<AccountManagementService.StaffAccountResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request,
            Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "admin_sistema";
        AccountManagementService.StaffAccountResponse updated = accountManagementService.updateStatus(
                id,
                request.status(),
                actor
        );
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/revoke-sessions")
    public ResponseEntity<Void> revokeSessions(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "admin_sistema";
        accountManagementService.revokeSessions(id, actor);
        return ResponseEntity.noContent().build();
    }
}
