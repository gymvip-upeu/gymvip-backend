package com.gymvip.controller;

import com.gymvip.service.MemberWorkspaceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/member-app")
@PreAuthorize("hasRole('CLIENTE')")
public class MemberAppController {
    private final MemberWorkspaceService service;

    public MemberAppController(MemberWorkspaceService service) {
        this.service = service;
    }

    @GetMapping("/session")
    public Object session(Authentication auth) {
        return Map.of("mustChangePassword", service.mustChange(auth.getName()));
    }

    @PostMapping("/password")
    public Object password(@Valid @RequestBody MemberWorkspaceService.PasswordChange request, Authentication auth) {
        service.changePassword(auth.getName(), request);
        return Map.of("changed", true, "requiresLogin", true);
    }

    @GetMapping("/me")
    public Object me(Authentication auth) {
        if (service.mustChange(auth.getName())) {
            throw new org.springframework.security.access.AccessDeniedException("Cambia la contraseña temporal antes de continuar.");
        }
        return service.workspace(service.ownClient(auth.getName()));
    }

    @GetMapping("/attendance")
    public Object attendance(Authentication auth) {
        if (service.mustChange(auth.getName())) {
            throw new org.springframework.security.access.AccessDeniedException("Cambia la contraseña temporal antes de continuar.");
        }
        return service.attendanceHistory(service.ownClient(auth.getName()));
    }

    @GetMapping("/qr")
    public Object dynamicQr(Authentication auth) {
        if (service.mustChange(auth.getName())) {
            throw new org.springframework.security.access.AccessDeniedException("Cambia la contraseña temporal antes de continuar.");
        }
        return service.generateDynamicQr(service.ownClient(auth.getName()));
    }

    @GetMapping("/favorites")
    public Object listFavorites(Authentication auth) {
        if (service.mustChange(auth.getName())) {
            throw new org.springframework.security.access.AccessDeniedException("Cambia la contraseña temporal antes de continuar.");
        }
        return service.listFavorites(service.ownClient(auth.getName()));
    }

    @PostMapping("/favorites")
    public Object addFavorite(@Valid @RequestBody MemberWorkspaceService.FavoriteRequest request, Authentication auth) {
        if (service.mustChange(auth.getName())) {
            throw new org.springframework.security.access.AccessDeniedException("Cambia la contraseña temporal antes de continuar.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addFavorite(service.ownClient(auth.getName()), request));
    }

    @PutMapping("/favorites/{id}")
    public Object updateFavoriteNote(@PathVariable UUID id, @Valid @RequestBody MemberWorkspaceService.FavoriteNoteUpdate request, Authentication auth) {
        if (service.mustChange(auth.getName())) {
            throw new org.springframework.security.access.AccessDeniedException("Cambia la contraseña temporal antes de continuar.");
        }
        return service.updateFavoriteNote(service.ownClient(auth.getName()), id, request.notes());
    }

    @DeleteMapping("/favorites/{id}")
    public Object removeFavorite(@PathVariable UUID id, Authentication auth) {
        if (service.mustChange(auth.getName())) {
            throw new org.springframework.security.access.AccessDeniedException("Cambia la contraseña temporal antes de continuar.");
        }
        return service.removeFavorite(service.ownClient(auth.getName()), id);
    }
}
