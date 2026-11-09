package com.gymvip.controller;

import com.gymvip.dto.request.PasswordResetConfirmRequest;
import com.gymvip.dto.request.PasswordResetRequest;
import com.gymvip.dto.response.PasswordResetConfirmResponse;
import com.gymvip.dto.response.PasswordResetResponse;
import com.gymvip.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/password-reset")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/request")
    public ResponseEntity<PasswordResetResponse> requestReset(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = resolveClientIp(httpRequest);
        PasswordResetResponse response = passwordResetService.requestReset(request, clientIp);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/confirm")
    public ResponseEntity<PasswordResetConfirmResponse> confirmReset(
            @Valid @RequestBody PasswordResetConfirmRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = resolveClientIp(httpRequest);
        PasswordResetConfirmResponse response = passwordResetService.confirmReset(request, clientIp);
        return ResponseEntity.ok(response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
