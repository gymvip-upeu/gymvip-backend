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
@PreAuthorize("hasAnyRole('CLIENTE','ADMIN','RECEPCION')")
public class MemberAppController {
    private final MemberWorkspaceService service;
    private final com.gymvip.service.AttendanceProcessingService attendanceProcessingService;

    public MemberAppController(
            MemberWorkspaceService service,
            com.gymvip.service.AttendanceProcessingService attendanceProcessingService
    ) {
        this.service = service;
        this.attendanceProcessingService = attendanceProcessingService;
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

    @GetMapping("/faqs")
    public Object faqs() {
        return service.listFaqs();
    }

    @GetMapping("/services")
    public Object services() {
        return service.listServices();
    }

    @GetMapping("/inbox")
    @PreAuthorize("hasRole('CLIENTE')")
    public Object inbox(Authentication auth) {
        if (service.mustChange(auth.getName())) {
            throw new org.springframework.security.access.AccessDeniedException("Cambia la contraseña temporal antes de continuar.");
        }
        return service.listInbox(service.ownClient(auth.getName()));
    }

    @PostMapping("/inbox/{id}/read")
    @PreAuthorize("hasRole('CLIENTE')")
    public Object markInboxRead(@PathVariable String id, Authentication auth) {
        if (service.mustChange(auth.getName())) {
            throw new org.springframework.security.access.AccessDeniedException("Cambia la contraseña temporal antes de continuar.");
        }
        return service.markInboxRead(service.ownClient(auth.getName()), id);
    }

    @GetMapping("/consents")
    @PreAuthorize("hasRole('CLIENTE')")
    public Object getConsents(Authentication auth) {
        return service.getConsents(service.ownClient(auth.getName()));
    }

    @PostMapping("/consents")
    @PreAuthorize("hasRole('CLIENTE')")
    public Object saveConsents(@Valid @RequestBody MemberWorkspaceService.ConsentRequest request, Authentication auth) {
        return service.saveConsents(service.ownClient(auth.getName()), request);
    }

    @PostMapping("/delete-account")
    public ResponseEntity<MemberWorkspaceService.DeleteRequestResult> deleteAccount(
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth
    ) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new org.springframework.security.access.AccessDeniedException("Autenticación requerida para solicitar supresión de cuenta.");
        }
        String reason = (body != null && body.containsKey("reason")) ? body.get("reason") : "Solicitud voluntaria del titular";
        UUID clientId = service.ownClient(auth.getName());
        return ResponseEntity.ok(service.requestAccountDeletion(clientId, reason));
    }

    @PostMapping("/devices")
    public ResponseEntity<?> registerDevice(@Valid @RequestBody MemberWorkspaceService.DeviceTokenRegistration request, Authentication auth) {
        if (service.mustChange(auth.getName())) {
            throw new org.springframework.security.access.AccessDeniedException("Cambia la contraseña temporal antes de continuar.");
        }
        UUID clientId = service.ownClient(auth.getName());
        return ResponseEntity.ok(service.registerDeviceToken(clientId, request));
    }

    @PostMapping("/attendance/check-in")
    public com.gymvip.dto.attendance.AttendanceDtos.MemberCheckInResponse selfCheckIn(
            @Valid @RequestBody com.gymvip.dto.attendance.AttendanceDtos.MemberSelfCheckInRequest request,
            Authentication auth,
            jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        if (service.mustChange(auth.getName())) {
            throw new org.springframework.security.access.AccessDeniedException("Cambia la contraseña temporal antes de continuar.");
        }
        String ip = httpRequest != null ? httpRequest.getRemoteAddr() : "0.0.0.0";
        return attendanceProcessingService.processMemberSelfCheckIn(
                auth.getName(),
                request.challenge(),
                request.requestId(),
                ip
        );
    }
}
