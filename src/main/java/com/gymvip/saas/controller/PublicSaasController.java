package com.gymvip.saas.controller;

import com.gymvip.exception.ResourceNotFoundException;
import com.gymvip.saas.repository.B2CExperienceRepository;
import com.gymvip.saas.repository.SaasPlatformRepository;
import com.gymvip.saas.service.CompanyInvitationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class PublicSaasController {

    private final SaasPlatformRepository saasPlatformRepository;
    private final B2CExperienceRepository b2cRepository;
    private final CompanyInvitationService invitations;

    public PublicSaasController(SaasPlatformRepository saasPlatformRepository,
                                B2CExperienceRepository b2cRepository, CompanyInvitationService invitations) {
        this.saasPlatformRepository = saasPlatformRepository;
        this.b2cRepository = b2cRepository;
        this.invitations = invitations;
    }

    @GetMapping("/api/v1/public/gyms")
    public ResponseEntity<List<Map<String, Object>>> publicGyms(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district
    ) {
        return ResponseEntity.ok(b2cRepository.listPublicGymsShowcase(q, city, district));
    }

    @GetMapping("/api/v1/public/gyms/{slugOrId}")
    public ResponseEntity<Map<String, Object>> publicGymDetail(@PathVariable String slugOrId) {
        return ResponseEntity.ok(b2cRepository.getPublicGymDetail(slugOrId));
    }

    @GetMapping("/api/v1/public/saas-plans")
    public ResponseEntity<List<Map<String, Object>>> publicSaasPlans() {
        return ResponseEntity.ok(saasPlatformRepository.listSaasPlans());
    }

    public record PublicCompanyOnboardingRequest(
            @NotBlank String businessName,
            String commercialName,
            @NotBlank String ruc,
            @NotBlank String city,
            @NotBlank String district,
            String address,
            @NotBlank String contactName,
            @NotBlank @Email String contactEmail,
            @NotBlank String contactPhone,
            Integer branchesCount,
            Integer estimatedMembers,
            String requestedPlanCode,
            String message,
            boolean legalAccepted,
            String legalVersion
    ) {}

    @PostMapping("/api/v1/public/company-requests")
    public ResponseEntity<Map<String, Object>> submitCompanyRequest(@Valid @RequestBody PublicCompanyOnboardingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(saasPlatformRepository.submitCompanyRequest(
                request.businessName(),
                request.commercialName(),
                request.ruc(),
                request.city(),
                request.district(),
                request.address(),
                request.contactName(),
                request.contactEmail(),
                request.contactPhone(),
                request.branchesCount() != null ? request.branchesCount() : 1,
                request.estimatedMembers() != null ? request.estimatedMembers() : 100,
                request.requestedPlanCode(),
                request.message(),
                request.legalAccepted(),
                request.legalVersion()
        ));
    }

    @GetMapping("/api/v1/public/company-requests/{trackingCode}")
    public ResponseEntity<Map<String, Object>> trackCompanyRequest(@PathVariable String trackingCode) {
        return ResponseEntity.ok(saasPlatformRepository.trackCompanyRequest(trackingCode)
                .orElseThrow(() -> new ResourceNotFoundException("Código de seguimiento no encontrado.")));
    }

    @GetMapping("/api/v1/legal/documents")
    public ResponseEntity<List<Map<String, Object>>> legalDocuments() {
        return ResponseEntity.ok(b2cRepository.listActiveLegalDocuments());
    }

    public record CompanyActivationRequest(
            @NotBlank @jakarta.validation.constraints.Pattern(regexp = "[a-f0-9]{64}") String token,
            @NotBlank @jakarta.validation.constraints.Size(max = 72) String password
    ) {}

    @PostMapping("/api/v1/public/company-activation")
    public ResponseEntity<Void> activateCompanyAccount(@Valid @RequestBody CompanyActivationRequest request) {
        invitations.activate(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/app/releases/latest")
    public ResponseEntity<Map<String, Object>> latestAppRelease(
            @RequestParam(defaultValue = "ANDROID") String platform,
            @RequestParam(defaultValue = "PRODUCTION") String channel,
            @RequestParam(required = false) Integer currentVersionCode
    ) {
        return ResponseEntity.ok(b2cRepository.getLatestAppRelease(platform, channel, currentVersionCode));
    }
}
