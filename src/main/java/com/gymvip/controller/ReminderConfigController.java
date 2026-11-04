package com.gymvip.controller;

import com.gymvip.dto.request.ReminderConfigRequest;
import com.gymvip.dto.response.ReminderConfigResponse;
import com.gymvip.service.ReminderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Endpoints REST para la configuración y evaluación de recordatorios automáticos.
 * HU-14 T05.
 */
@RestController
@RequestMapping("/api/v1/reminders")
public class ReminderConfigController {

    private final ReminderService reminderService;

    public ReminderConfigController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping("/config")
    public ResponseEntity<ReminderConfigResponse> getConfig() {
        return ResponseEntity.ok(reminderService.getConfig());
    }

    @PutMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReminderConfigResponse> updateConfig(
            @Valid @RequestBody ReminderConfigRequest request,
            Authentication auth
    ) {
        return ResponseEntity.ok(reminderService.updateConfig(request, auth.getName()));
    }

    @PostMapping("/evaluate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReminderService.EvaluationSummary> evaluateNow(Authentication auth) {
        return ResponseEntity.ok(reminderService.evaluateReminders(auth.getName()));
    }

    @GetMapping("/cuts")
    public ResponseEntity<List<Map<String, Object>>> getCutsHistory() {
        return ResponseEntity.ok(reminderService.getCutsHistory());
    }
}
