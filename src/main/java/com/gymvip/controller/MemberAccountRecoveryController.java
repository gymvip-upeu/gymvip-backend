package com.gymvip.controller;

import com.gymvip.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class MemberAccountRecoveryController {
    private final PasswordResetService service;
    public MemberAccountRecoveryController(PasswordResetService service){this.service=service;}
    @PostMapping("/api/v1/member-app/password-reset")
    @PreAuthorize("hasAnyRole('CLIENTE','ADMIN','RECEPCION')")
    public ResponseEntity<?> request(Authentication auth,HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(service.requestOwnReset(auth.getName(),request.getRemoteAddr()));
    }
}
