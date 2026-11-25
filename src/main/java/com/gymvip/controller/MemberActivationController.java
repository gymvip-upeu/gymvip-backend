package com.gymvip.controller;

import com.gymvip.service.MemberAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
public class MemberActivationController {
    private final MemberAccessService access;

    public MemberActivationController(MemberAccessService access) {
        this.access = access;
    }

    public record Activation(
            @com.fasterxml.jackson.annotation.JsonAlias("activationToken") @NotBlank @Size(max = 200) String token,
            @NotBlank String newPassword
    ) {}

    @PostMapping("/api/v1/auth/activate")
    public ResponseEntity<?> activate(@Valid @RequestBody Activation request) {
        access.activate(request.token(), request.newPassword());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
                "activated", true,
                "requiresLogin", true,
                "message", "Tu cuenta está activa. Ingresa en la app con tu correo y tu nueva contraseña."
        ));
    }

    @GetMapping("/api/v1/members/access-invitations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCION')")
    public ResponseEntity<?> status(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(access.deliveryStatus(id));
    }

    @PostMapping("/api/v1/members/access-invitations/{id}/resend")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCION')")
    public ResponseEntity<?> resend(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(access.resend(id, auth.getName()));
    }
}
