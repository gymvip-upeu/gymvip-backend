package com.gymvip.controller;

import com.gymvip.enterprise.UserPreferenceEntity;
import com.gymvip.enterprise.UserPreferenceRepository;
import com.gymvip.entity.Account;
import com.gymvip.repository.AccountRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/preferences")
public class PreferencesController {

    private final AccountRepository accountRepository;
    private final UserPreferenceRepository preferenceRepository;

    public PreferencesController(AccountRepository accountRepository, UserPreferenceRepository preferenceRepository) {
        this.accountRepository = accountRepository;
        this.preferenceRepository = preferenceRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getPreferences(Authentication authentication) {
        if (authentication == null) return ResponseEntity.status(401).build();
        Account account = accountRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        if (account == null) return ResponseEntity.status(404).build();

        UserPreferenceEntity pref = preferenceRepository.findById(account.getId()).orElse(null);
        return ResponseEntity.ok(toDto(pref));
    }

    public record UpdatePreferencesRequest(Boolean showGuides, String experienceMode, UUID preferredBranchId) {}

    @PutMapping
    public ResponseEntity<Map<String, Object>> updatePreferences(
            @RequestBody UpdatePreferencesRequest request,
            Authentication authentication
    ) {
        if (authentication == null) return ResponseEntity.status(401).build();
        Account account = accountRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        if (account == null) return ResponseEntity.status(404).build();

        UserPreferenceEntity pref = preferenceRepository.findById(account.getId()).orElseGet(() -> {
            UserPreferenceEntity p = new UserPreferenceEntity();
            p.setAccountId(account.getId());
            return p;
        });

        if (request.showGuides() != null) {
            pref.setShowGuides(request.showGuides());
            pref.setExperienceMode(request.showGuides() ? "GUIDED" : "COMPACT");
        }
        if (request.experienceMode() != null && !request.experienceMode().isBlank()) {
            String mode = request.experienceMode().trim().toUpperCase();
            if ("COMPACT".equals(mode) || "GUIDED".equals(mode)) {
                pref.setExperienceMode(mode);
                pref.setShowGuides("GUIDED".equals(mode));
            }
        }
        if (request.preferredBranchId() != null) {
            pref.setPreferredBranchId(request.preferredBranchId());
        }
        pref.setUpdatedAt(LocalDateTime.now());
        UserPreferenceEntity saved = preferenceRepository.save(pref);
        return ResponseEntity.ok(toDto(saved));
    }

    private Map<String, Object> toDto(UserPreferenceEntity pref) {
        Map<String, Object> map = new LinkedHashMap<>();
        boolean showGuides = pref == null || pref.isShowGuides();
        map.put("showGuides", showGuides);
        map.put("experienceMode", pref != null ? pref.getExperienceMode() : "GUIDED");
        map.put("preferredBranchId", pref != null ? pref.getPreferredBranchId() : null);
        return map;
    }
}
