package com.gymvip.controller;

import com.gymvip.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemberAccountRecoveryController {
    private final PasswordResetService service;

    public MemberAccountRecoveryController(PasswordResetService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/member-app/password-reset")
    @PreAuthorize("hasAnyRole('CLIENTE','ADMIN','GERENTE','RECEPCION','AUDITOR')")
    public ResponseEntity<?> request(Authentication auth, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.requestOwnReset(auth.getName(), request.getRemoteAddr()));
    }
}
