package com.gymvip.saas.controller;

import com.gymvip.saas.repository.B2CExperienceRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CommercialAndGroupController {

    private final B2CExperienceRepository repository;

    public CommercialAndGroupController(B2CExperienceRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/organization/commercial-profile")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ADMIN_EMPRESA','GERENTE_GENERAL','GERENTE')")
    public ResponseEntity<Map<String, Object>> getCommercialProfile(
            @RequestParam(required = false) UUID organizationId,
            Authentication auth
    ) {
        return ResponseEntity.ok(repository.getCommercialProfileForActor(auth.getName(), organizationId));
    }

    public record CommercialProfileRequest(
            UUID organizationId,
            String slug,
            String headline,
            String tagline,
            String aboutText,
            String amenitiesJson,
            String scheduleJson,
            String galleryUrlsJson,
            String referencePlansJson,
            String contactWhatsapp,
            String instagramHandle,
            String websiteUrl,
            String publishStatus
    ) {}

    @PutMapping("/organization/commercial-profile")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ADMIN_EMPRESA','GERENTE_GENERAL','GERENTE')")
    public ResponseEntity<Map<String, Object>> saveCommercialProfile(
            @RequestBody CommercialProfileRequest request,
            Authentication auth
    ) {
        return ResponseEntity.ok(repository.saveCommercialProfile(
                auth.getName(),
                request.organizationId(),
                request.slug(),
                request.headline(),
                request.tagline(),
                request.aboutText(),
                request.amenitiesJson(),
                request.scheduleJson(),
                request.galleryUrlsJson(),
                request.referencePlansJson(),
                request.contactWhatsapp(),
                request.instagramHandle(),
                request.websiteUrl(),
                request.publishStatus()
        ));
    }

    @GetMapping("/group-promotions")
    public ResponseEntity<List<Map<String, Object>>> listGroupPromotions(
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(defaultValue = "true") boolean onlyActive
    ) {
        return ResponseEntity.ok(repository.listGroupPromotions(organizationId, onlyActive));
    }

    public record CreateGroupPromotionRequest(
            UUID organizationId,
            @NotBlank String title,
            String description,
            UUID planId,
            int minParticipants,
            int maxParticipants,
            @NotNull BigDecimal discountPercent,
            LocalDate validFrom,
            LocalDate validUntil,
            String terms
    ) {}

    @PostMapping("/group-promotions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ADMIN_EMPRESA','GERENTE_GENERAL','GERENTE_SEDE','GERENTE')")
    public ResponseEntity<Map<String, Object>> createGroupPromotion(
            @Valid @RequestBody CreateGroupPromotionRequest request,
            Authentication auth
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.createGroupPromotion(
                auth.getName(), request.organizationId(), request.title(), request.description(),
                request.planId(), request.minParticipants(), request.maxParticipants(),
                request.discountPercent(), request.validFrom(), request.validUntil(), request.terms()
        ));
    }

    public record CreateGroupEnrollmentRequest(
            @NotNull UUID promotionId,
            BigDecimal referenceBasePricePen,
            List<String> invitedEmails
    ) {}

    @PostMapping("/group-enrollments")
    public ResponseEntity<Map<String, Object>> createGroupEnrollment(
            @Valid @RequestBody CreateGroupEnrollmentRequest request,
            Authentication auth
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.createGroupEnrollment(
                auth.getName(), request.promotionId(), request.referenceBasePricePen(), request.invitedEmails()
        ));
    }

    @GetMapping("/group-enrollments/my")
    public ResponseEntity<List<Map<String, Object>>> myGroupEnrollments(Authentication auth) {
        return ResponseEntity.ok(repository.listGroupEnrollmentsForActor(auth.getName()));
    }

    public record JoinGroupEnrollmentRequest(boolean acceptConsent) {}

    @PostMapping("/group-enrollments/{invitationCode}/join")
    public ResponseEntity<Map<String, Object>> joinGroupEnrollment(
            @PathVariable String invitationCode,
            @RequestBody JoinGroupEnrollmentRequest request,
            Authentication auth
    ) {
        return ResponseEntity.ok(repository.joinGroupEnrollment(auth.getName(), invitationCode, request.acceptConsent()));
    }

    @PostMapping("/group-enrollments/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelGroupEnrollment(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(repository.cancelGroupEnrollment(auth.getName(), id));
    }

    @GetMapping("/gyms/{organizationId}/reviews")
    public ResponseEntity<List<Map<String, Object>>> listGymReviews(@PathVariable UUID organizationId) {
        return ResponseEntity.ok(repository.listReviewsForOrganization(organizationId));
    }

    public record CreateReviewRequest(
            UUID branchId,
            @Min(1) @Max(5) int ratingOverall,
            @Min(1) @Max(5) int ratingCleanliness,
            @Min(1) @Max(5) int ratingEquipment,
            @Min(1) @Max(5) int ratingAttention,
            @Min(1) @Max(5) int ratingValue,
            @NotBlank String comment
    ) {}

    @PostMapping("/gyms/{organizationId}/reviews")
    public ResponseEntity<Map<String, Object>> createGymReview(
            @PathVariable UUID organizationId,
            @Valid @RequestBody CreateReviewRequest request,
            Authentication auth
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.createVerifiedReview(
                auth.getName(), organizationId, request.branchId(), request.ratingOverall(),
                request.ratingCleanliness(), request.ratingEquipment(), request.ratingAttention(),
                request.ratingValue(), request.comment()
        ));
    }

    public record ReplyReviewRequest(@NotBlank String replyText) {}

    @PostMapping("/gyms/{organizationId}/reviews/{reviewId}/reply")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ADMIN_EMPRESA','GERENTE_GENERAL','GERENTE_SEDE','GERENTE')")
    public ResponseEntity<Map<String, Object>> replyGymReview(
            @PathVariable UUID organizationId,
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReplyReviewRequest request,
            Authentication auth
    ) {
        return ResponseEntity.ok(repository.replyToReview(auth.getName(), organizationId, reviewId, request.replyText()));
    }
}
