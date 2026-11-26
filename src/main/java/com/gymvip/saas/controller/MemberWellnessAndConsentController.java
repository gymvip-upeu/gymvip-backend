package com.gymvip.saas.controller;

import com.gymvip.saas.repository.B2CExperienceRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/member-app")
public class MemberWellnessAndConsentController {

    private final B2CExperienceRepository repository;

    public MemberWellnessAndConsentController(B2CExperienceRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/physical-profile")
    public ResponseEntity<Map<String, Object>> getPhysicalProfile(Authentication auth) {
        return ResponseEntity.ok(repository.getPhysicalProfile(auth.getName()));
    }

    public record PhysicalProfileRequest(
            BigDecimal heightCm,
            BigDecimal weightKg,
            String primaryGoal,
            String experienceLevel,
            Integer weeklyAvailabilityDays,
            String injuriesOrNotes,
            boolean consentSaved
    ) {}

    @PutMapping("/physical-profile")
    public ResponseEntity<Map<String, Object>> savePhysicalProfile(
            @RequestBody PhysicalProfileRequest request,
            Authentication auth
    ) {
        return ResponseEntity.ok(repository.upsertPhysicalProfile(
                auth.getName(), request.heightCm(), request.weightKg(), request.primaryGoal(),
                request.experienceLevel(), request.weeklyAvailabilityDays(), request.injuriesOrNotes(),
                request.consentSaved()
        ));
    }

    @DeleteMapping("/physical-profile")
    public ResponseEntity<Void> deletePhysicalProfile(Authentication auth) {
        repository.deletePhysicalProfile(auth.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/weekly-plans")
    public ResponseEntity<List<Map<String, Object>>> listWeeklyPlans(Authentication auth) {
        return ResponseEntity.ok(repository.listWeeklyPlans(auth.getName()));
    }

    public record WeeklyPlanRequest(
            UUID id,
            @NotBlank String title,
            @NotBlank String goal,
            String level,
            LocalDate weekStartDate,
            String daysJson
    ) {}

    @PostMapping("/weekly-plans")
    public ResponseEntity<Map<String, Object>> saveWeeklyPlan(
            @Valid @RequestBody WeeklyPlanRequest request,
            Authentication auth
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.saveWeeklyPlan(
                auth.getName(), request.id(), request.title(), request.goal(),
                request.level(), request.weekStartDate(), request.daysJson()
        ));
    }

    @DeleteMapping("/weekly-plans/{id}")
    public ResponseEntity<Void> deleteWeeklyPlan(@PathVariable UUID id, Authentication auth) {
        repository.deactivateWeeklyPlan(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/notification-preferences")
    public ResponseEntity<Map<String, Object>> getNotificationPreferences(Authentication auth) {
        return ResponseEntity.ok(repository.getNotificationPreferences(auth.getName()));
    }

    public record NotificationPreferencesRequest(
            boolean channelPushOperational,
            boolean channelPushReminders,
            boolean channelPushPromotions,
            boolean channelEmailOperational,
            boolean channelEmailPromotions,
            boolean channelWhatsappOperational,
            boolean channelWhatsappPromotions
    ) {}

    @PutMapping("/notification-preferences")
    public ResponseEntity<Map<String, Object>> updateNotificationPreferences(
            @RequestBody NotificationPreferencesRequest request,
            Authentication auth
    ) {
        return ResponseEntity.ok(repository.updateNotificationPreferences(
                auth.getName(),
                request.channelPushOperational(),
                request.channelPushReminders(),
                request.channelPushPromotions(),
                request.channelEmailOperational(),
                request.channelEmailPromotions(),
                request.channelWhatsappOperational(),
                request.channelWhatsappPromotions()
        ));
    }

    @GetMapping("/legal-acceptances")
    public ResponseEntity<List<Map<String, Object>>> listLegalAcceptances(Authentication auth) {
        return ResponseEntity.ok(repository.listAccountLegalAcceptances(auth.getName()));
    }

    public record LegalAcceptanceRequest(
            @NotBlank String documentCode,
            @NotBlank String documentVersion,
            boolean accepted,
            String purpose
    ) {}

    @PostMapping("/legal-acceptances")
    public ResponseEntity<Map<String, Object>> recordLegalAcceptance(
            @Valid @RequestBody LegalAcceptanceRequest request,
            Authentication auth,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.recordLegalAcceptance(
                auth.getName(), request.documentCode(), request.documentVersion(),
                request.accepted(), request.purpose(),
                servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent")
        ));
    }
}
