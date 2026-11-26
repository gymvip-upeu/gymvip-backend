package com.gymvip.saas.repository;

import com.gymvip.entity.Account;
import com.gymvip.exception.ConflictException;
import com.gymvip.exception.ResourceNotFoundException;
import com.gymvip.repository.AccountRepository;
import com.gymvip.saas.domain.SaasCalculations;
import com.gymvip.service.MemberWorkspaceService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Repository
public class B2CExperienceRepository {

    private static final Set<String> VALID_PUBLISH_STATES = Set.of("BORRADOR", "PUBLICADO", "OCULTO");
    private static final Set<String> OWNER_STAFF_ROLES = Set.of(
            "SUPER_ADMIN", "ADMIN", "ADMIN_EMPRESA", "GERENTE", "GERENTE_GENERAL", "GERENTE_SEDE", "RECEPCION"
    );

    private final JdbcTemplate jdbc;
    private final AccountRepository accountRepository;
    private final MemberWorkspaceService memberWorkspaceService;

    public B2CExperienceRepository(JdbcTemplate jdbc,
                                   AccountRepository accountRepository,
                                   MemberWorkspaceService memberWorkspaceService) {
        this.jdbc = jdbc;
        this.accountRepository = accountRepository;
        this.memberWorkspaceService = memberWorkspaceService;
    }

    public List<Map<String, Object>> listPublicGymsShowcase(String q, String city, String district) {
        List<Map<String, Object>> orgs = jdbc.queryForList(
                "SELECT o.id, o.code, o.name, o.description, o.plan_tier, " +
                        "cp.slug, cp.headline, cp.tagline, cp.about_text, cp.amenities_json, cp.schedule_json, " +
                        "cp.reference_plans_json, cp.contact_whatsapp, cp.instagram_handle, cp.website_url " +
                        "FROM organizations o " +
                        "LEFT JOIN organization_commercial_profiles cp ON cp.organization_id = o.id AND cp.publish_status = 'PUBLICADO' " +
                        "WHERE UPPER(o.status) = 'ACTIVO' AND o.is_published = TRUE " +
                        "ORDER BY o.name ASC"
        );

        Map<String, Object> pricingRange = jdbc.queryForMap(
                "SELECT MIN(price_pen) AS min_price_pen, MAX(price_pen) AS max_price_pen FROM plans WHERE status = 'ACTIVO'"
        );
        BigDecimal minPrice = pricingRange.get("min_price_pen") != null ? new BigDecimal(String.valueOf(pricingRange.get("min_price_pen"))) : null;
        BigDecimal maxPrice = pricingRange.get("max_price_pen") != null ? new BigDecimal(String.valueOf(pricingRange.get("max_price_pen"))) : null;

        List<Map<String, Object>> reviewAggs = jdbc.queryForList(
                "SELECT organization_id, COUNT(*) AS reviews_count, COALESCE(ROUND(AVG(rating_overall), 2), 0) AS avg_rating " +
                        "FROM gym_reviews WHERE status = 'PUBLICADA' GROUP BY organization_id"
        );
        Map<String, Map<String, Object>> aggByOrg = new HashMap<>();
        for (Map<String, Object> r : reviewAggs) {
            aggByOrg.put(String.valueOf(r.get("organization_id")), r);
        }

        String query = q != null ? q.trim().toLowerCase(Locale.ROOT) : "";
        String cityFilter = city != null ? city.trim().toLowerCase(Locale.ROOT) : "";
        String districtFilter = district != null ? district.trim().toLowerCase(Locale.ROOT) : "";

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> org : orgs) {
            UUID orgId = (UUID) org.get("id");
            List<Map<String, Object>> branches = jdbc.queryForList(
                    "SELECT id, code, name, city, district, address, phone, schedule_summary, services_summary, capacity " +
                            "FROM branches WHERE organization_id = ? AND is_published = TRUE AND UPPER(status) = 'ACTIVO' ORDER BY name ASC",
                    orgId
            ).stream().filter(b ->
                    (cityFilter.isEmpty() || String.valueOf(b.get("city")).toLowerCase(Locale.ROOT).contains(cityFilter))
                            && (districtFilter.isEmpty() || String.valueOf(b.get("district")).toLowerCase(Locale.ROOT).contains(districtFilter))
                            && (query.isEmpty()
                            || String.valueOf(org.get("name")).toLowerCase(Locale.ROOT).contains(query)
                            || String.valueOf(b.get("name")).toLowerCase(Locale.ROOT).contains(query)
                            || String.valueOf(b.get("district")).toLowerCase(Locale.ROOT).contains(query))
            ).map(this::camelizeMap).toList();

            if (!branches.isEmpty() || (cityFilter.isEmpty() && districtFilter.isEmpty() && query.isEmpty())) {
                Map<String, Object> dto = camelizeMap(org);
                dto.put("minPricePen", minPrice);
                dto.put("maxPricePen", maxPrice);
                dto.put("branches", branches);
                Map<String, Object> agg = aggByOrg.get(String.valueOf(orgId));
                dto.put("reviewsCount", agg != null ? ((Number) agg.get("reviews_count")).longValue() : 0L);
                dto.put("avgRating", agg != null ? new BigDecimal(String.valueOf(agg.get("avg_rating"))) : BigDecimal.ZERO);
                dto.put("activeGroupPromosCount", jdbc.queryForObject(
                        "SELECT COUNT(*) FROM group_promotions WHERE organization_id = ? AND status = 'ACTIVA' AND valid_until >= CURRENT_DATE",
                        Long.class, orgId
                ));
                result.add(dto);
            }
        }
        return result;
    }

    public Map<String, Object> getPublicGymDetail(String slugOrId) {
        List<Map<String, Object>> matches = listPublicGymsShowcase(null, null, null).stream()
                .filter(g -> slugOrId.equalsIgnoreCase(String.valueOf(g.get("id")))
                        || slugOrId.equalsIgnoreCase(String.valueOf(g.get("code")))
                        || slugOrId.equalsIgnoreCase(String.valueOf(g.get("slug"))))
                .toList();
        if (matches.isEmpty()) {
            throw new ResourceNotFoundException("Gimnasio no encontrado en el catálogo público.");
        }
        Map<String, Object> gym = new LinkedHashMap<>(matches.get(0));
        UUID orgId = (UUID) gym.get("id");
        gym.put("reviews", listReviewsForOrganization(orgId));
        gym.put("groupPromotions", listGroupPromotions(orgId, true));
        return gym;
    }

    public Map<String, Object> getCommercialProfileForActor(String actorEmail, UUID requestedOrgId) {
        UUID orgId = resolveOrgIdForActor(actorEmail, requestedOrgId);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM organization_commercial_profiles WHERE organization_id = ?",
                orgId
        );
        if (rows.isEmpty()) {
            Map<String, Object> org = jdbc.queryForMap("SELECT id, code, name, description FROM organizations WHERE id = ?", orgId);
            Map<String, Object> blank = new LinkedHashMap<>();
            blank.put("organizationId", orgId);
            blank.put("slug", org.get("code"));
            blank.put("headline", org.get("name"));
            blank.put("tagline", "");
            blank.put("aboutText", org.get("description"));
            blank.put("amenitiesJson", "[]");
            blank.put("scheduleJson", "{}");
            blank.put("galleryUrlsJson", "[]");
            blank.put("referencePlansJson", "[]");
            blank.put("publishStatus", "BORRADOR");
            return blank;
        }
        return camelizeMap(rows.get(0));
    }

    @Transactional
    public Map<String, Object> saveCommercialProfile(String actorEmail, UUID requestedOrgId, String slug, String headline,
                                                     String tagline, String aboutText, String amenitiesJson,
                                                     String scheduleJson, String galleryUrlsJson, String referencePlansJson,
                                                     String contactWhatsapp, String instagramHandle, String websiteUrl,
                                                     String publishStatus) {
        UUID orgId = resolveOrgIdForActor(actorEmail, requestedOrgId);
        String normStatus = publishStatus != null && VALID_PUBLISH_STATES.contains(publishStatus.trim().toUpperCase(Locale.ROOT))
                ? publishStatus.trim().toUpperCase(Locale.ROOT)
                : "BORRADOR";
        if ("PUBLICADO".equals(normStatus) && (headline == null || headline.isBlank())) {
            throw new IllegalArgumentException("El titular comercial es obligatorio para publicar la ficha.");
        }
        String effSlug = slug != null && !slug.isBlank()
                ? slug.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-")
                : "gym-" + orgId.toString().substring(0, 8);

        jdbc.update(
                "INSERT INTO organization_commercial_profiles (organization_id, slug, headline, tagline, about_text, amenities_json, schedule_json, gallery_urls_json, reference_plans_json, contact_whatsapp, instagram_handle, website_url, publish_status, published_at_utc, updated_by, updated_at_utc) " +
                        "VALUES (?, ?, ?, ?, ?, COALESCE(?, '[]'), COALESCE(?, '{}'), COALESCE(?, '[]'), COALESCE(?, '[]'), ?, ?, ?, ?, CASE WHEN ? = 'PUBLICADO' THEN NOW() ELSE NULL END, ?, NOW()) " +
                        "ON CONFLICT (organization_id) DO UPDATE SET " +
                        "slug = EXCLUDED.slug, headline = EXCLUDED.headline, tagline = EXCLUDED.tagline, about_text = EXCLUDED.about_text, " +
                        "amenities_json = EXCLUDED.amenities_json, schedule_json = EXCLUDED.schedule_json, " +
                        "gallery_urls_json = EXCLUDED.gallery_urls_json, reference_plans_json = EXCLUDED.reference_plans_json, " +
                        "contact_whatsapp = EXCLUDED.contact_whatsapp, instagram_handle = EXCLUDED.instagram_handle, website_url = EXCLUDED.website_url, " +
                        "publish_status = EXCLUDED.publish_status, published_at_utc = CASE WHEN EXCLUDED.publish_status = 'PUBLICADO' THEN COALESCE(organization_commercial_profiles.published_at_utc, NOW()) ELSE organization_commercial_profiles.published_at_utc END, " +
                        "updated_by = EXCLUDED.updated_by, updated_at_utc = NOW()",
                orgId, effSlug, headline, tagline, aboutText, amenitiesJson, scheduleJson, galleryUrlsJson,
                referencePlansJson, contactWhatsapp, instagramHandle, websiteUrl, normStatus, normStatus, actorEmail
        );
        return getCommercialProfileForActor(actorEmail, orgId);
    }

    public List<Map<String, Object>> listGroupPromotions(UUID organizationId, boolean onlyActive) {
        StringBuilder sql = new StringBuilder(
                "SELECT gp.*, o.name AS organization_name FROM group_promotions gp JOIN organizations o ON o.id = gp.organization_id WHERE 1=1 "
        );
        List<Object> params = new ArrayList<>();
        if (organizationId != null) {
            sql.append("AND gp.organization_id = ? ");
            params.add(organizationId);
        }
        if (onlyActive) {
            sql.append("AND gp.status = 'ACTIVA' AND gp.valid_until >= CURRENT_DATE ");
        }
        sql.append("ORDER BY gp.created_at_utc DESC");
        return jdbc.queryForList(sql.toString(), params.toArray()).stream().map(this::camelizeMap).toList();
    }

    @Transactional
    public Map<String, Object> createGroupPromotion(String actorEmail, UUID requestedOrgId, String title, String description,
                                                    UUID planId, int minParticipants, int maxParticipants,
                                                    BigDecimal discountPercent, LocalDate validFrom, LocalDate validUntil,
                                                    String terms) {
        UUID orgId = resolveOrgIdForActor(actorEmail, requestedOrgId);
        if (minParticipants < 2 || maxParticipants < minParticipants) {
            throw new IllegalArgumentException("El rango de participantes debe ser al menos 2 y max >= min.");
        }
        if (discountPercent == null || discountPercent.compareTo(BigDecimal.ZERO) <= 0 || discountPercent.compareTo(BigDecimal.valueOf(60)) > 0) {
            throw new IllegalArgumentException("El descuento grupal debe ubicarse entre 1% y 60%.");
        }
        LocalDate from = validFrom != null ? validFrom : LocalDate.now();
        LocalDate until = validUntil != null ? validUntil : from.plusDays(60);
        if (!until.isAfter(from)) {
            throw new IllegalArgumentException("La fecha de fin de la promoción grupal debe ser posterior al inicio.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO group_promotions (id, organization_id, title, description, plan_id, min_participants, max_participants, discount_percent, valid_from, valid_until, status, terms, created_by) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVA', ?, ?)",
                id, orgId, title.trim(), description, planId, minParticipants, maxParticipants,
                discountPercent, Date.valueOf(from), Date.valueOf(until), terms, actorEmail
        );
        return camelizeMap(jdbc.queryForMap("SELECT * FROM group_promotions WHERE id = ?", id));
    }

    @Transactional
    public Map<String, Object> createGroupEnrollment(String actorEmail, UUID promotionId, BigDecimal referenceBasePricePen,
                                                     List<String> invitedEmails) {
        Account leader = accountRepository.findByEmailIgnoreCase(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada."));
        Map<String, Object> promo = jdbc.queryForMap("SELECT * FROM group_promotions WHERE id = ?", promotionId);
        if (!"ACTIVA".equalsIgnoreCase(String.valueOf(promo.get("status")))) {
            throw new ConflictException("La promoción grupal seleccionada no se encuentra activa.");
        }
        BigDecimal basePrice = referenceBasePricePen;
        if (promo.get("plan_id") != null && basePrice == null) {
            List<BigDecimal> prices = jdbc.queryForList("SELECT price_pen FROM plans WHERE id = ?", BigDecimal.class, promo.get("plan_id"));
            if (!prices.isEmpty()) basePrice = prices.get(0);
        }
        if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0) {
            basePrice = new BigDecimal("120.00");
        }
        BigDecimal discounted = SaasCalculations.calculateGroupDiscountedPrice(
                basePrice, new BigDecimal(String.valueOf(promo.get("discount_percent")))
        );

        int maxP = ((Number) promo.get("max_participants")).intValue();
        List<String> normalizedInvites = invitedEmails == null ? List.of() : invitedEmails.stream()
                .filter(Objects::nonNull)
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isBlank() && !s.equals(leader.getEmail().toLowerCase(Locale.ROOT)))
                .distinct()
                .toList();
        if (1 + normalizedInvites.size() > maxP) {
            throw new IllegalArgumentException("El grupo excede el máximo de " + maxP + " integrantes permitido por esta promoción.");
        }

        UUID enrollmentId = UUID.randomUUID();
        String code = "GRP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC).plusHours(48);

        jdbc.update(
                "INSERT INTO group_enrollments (id, promotion_id, organization_id, invitation_code, leader_account_id, status, base_price_per_person_pen, discounted_price_per_person_pen, expires_at_utc) " +
                        "VALUES (?, ?, ?, ?, ?, 'ABIERTA', ?, ?, ?)",
                enrollmentId, promotionId, promo.get("organization_id"), code, leader.getId(),
                basePrice, discounted, Timestamp.valueOf(expiresAt)
        );
        jdbc.update(
                "INSERT INTO group_enrollment_members (id, group_enrollment_id, account_id, invited_email, member_role, status, accepted_consent, joined_at_utc) " +
                        "VALUES (?, ?, ?, ?, 'LIDER', 'ACEPTADO', TRUE, NOW())",
                UUID.randomUUID(), enrollmentId, leader.getId(), leader.getEmail().toLowerCase(Locale.ROOT)
        );
        for (String inviteEmail : normalizedInvites) {
            jdbc.update(
                    "INSERT INTO group_enrollment_members (id, group_enrollment_id, invited_email, member_role, status, accepted_consent) " +
                            "VALUES (?, ?, ?, 'INVITADO', 'INVITADO', FALSE)",
                    UUID.randomUUID(), enrollmentId, inviteEmail
            );
        }
        return getGroupEnrollmentById(enrollmentId);
    }

    @Transactional
    public Map<String, Object> joinGroupEnrollment(String actorEmail, String invitationCode, boolean acceptConsent) {
        if (!acceptConsent) {
            throw new IllegalArgumentException("Cada integrante debe aceptar explícitamente el enlace grupal.");
        }
        Account user = accountRepository.findByEmailIgnoreCase(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada."));
        Map<String, Object> ge = jdbc.queryForMap(
                "SELECT * FROM group_enrollments WHERE UPPER(invitation_code) = UPPER(?)",
                invitationCode.trim()
        );
        if ("CANCELADA".equalsIgnoreCase(String.valueOf(ge.get("status"))) || "EXPIRADA".equalsIgnoreCase(String.valueOf(ge.get("status")))) {
            throw new ConflictException("La inscripción grupal ya fue cancelada o expiró.");
        }
        UUID enrollmentId = (UUID) ge.get("id");
        Timestamp expires = (Timestamp) ge.get("expires_at_utc");
        if (expires != null && expires.toLocalDateTime().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            jdbc.update("UPDATE group_enrollments SET status = 'EXPIRADA' WHERE id = ?", enrollmentId);
            throw new ConflictException("La invitación grupal superó su plazo de vigencia de 48h.");
        }

        Map<String, Object> promo = jdbc.queryForMap("SELECT min_participants, max_participants FROM group_promotions WHERE id = ?", ge.get("promotion_id"));
        int minP = ((Number) promo.get("min_participants")).intValue();
        int maxP = ((Number) promo.get("max_participants")).intValue();

        String email = user.getEmail().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> existingMember = jdbc.queryForList(
                "SELECT id, status FROM group_enrollment_members WHERE group_enrollment_id = ? AND invited_email = ?",
                enrollmentId, email
        );
        if (!existingMember.isEmpty()) {
            jdbc.update(
                    "UPDATE group_enrollment_members SET account_id = ?, status = 'ACEPTADO', accepted_consent = TRUE, joined_at_utc = NOW() WHERE id = ?",
                    user.getId(), existingMember.get(0).get("id")
            );
        } else {
            Long currentActiveCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM group_enrollment_members WHERE group_enrollment_id = ? AND status IN ('ACEPTADO','INVITADO')",
                    Long.class, enrollmentId
            );
            if (currentActiveCount != null && currentActiveCount >= maxP) {
                throw new ConflictException("El grupo ya alcanzó el límite máximo de integrantes.");
            }
            jdbc.update(
                    "INSERT INTO group_enrollment_members (id, group_enrollment_id, account_id, invited_email, member_role, status, accepted_consent, joined_at_utc) " +
                            "VALUES (?, ?, ?, ?, 'INVITADO', 'ACEPTADO', TRUE, NOW())",
                    UUID.randomUUID(), enrollmentId, user.getId(), email
            );
        }

        Long acceptedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM group_enrollment_members WHERE group_enrollment_id = ? AND status = 'ACEPTADO'",
                Long.class, enrollmentId
        );
        if (acceptedCount != null && acceptedCount >= minP) {
            jdbc.update("UPDATE group_enrollments SET status = 'COMPLETA' WHERE id = ? AND status = 'ABIERTA'", enrollmentId);
        }
        return getGroupEnrollmentById(enrollmentId);
    }

    @Transactional
    public Map<String, Object> cancelGroupEnrollment(String actorEmail, UUID enrollmentId) {
        Account user = accountRepository.findByEmailIgnoreCase(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada."));
        Map<String, Object> ge = jdbc.queryForMap("SELECT * FROM group_enrollments WHERE id = ?", enrollmentId);
        if (!user.getId().equals(ge.get("leader_account_id")) && !isPrivilegedStaff(user)) {
            throw new AccessDeniedException("Solo el líder del grupo o administración puede cancelar esta solicitud.");
        }
        jdbc.update("UPDATE group_enrollments SET status = 'CANCELADA' WHERE id = ?", enrollmentId);
        jdbc.update("UPDATE group_enrollment_members SET status = 'CANCELADO' WHERE group_enrollment_id = ?", enrollmentId);
        return getGroupEnrollmentById(enrollmentId);
    }

    public List<Map<String, Object>> listGroupEnrollmentsForActor(String actorEmail) {
        Account user = accountRepository.findByEmailIgnoreCase(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada."));
        List<UUID> ids = jdbc.queryForList(
                "SELECT DISTINCT ge.id FROM group_enrollments ge " +
                        "LEFT JOIN group_enrollment_members gem ON gem.group_enrollment_id = ge.id " +
                        "WHERE ge.leader_account_id = ? OR gem.account_id = ? OR LOWER(gem.invited_email) = LOWER(?) " +
                        "ORDER BY ge.id",
                UUID.class, user.getId(), user.getId(), user.getEmail()
        );
        return ids.stream().map(this::getGroupEnrollmentById).toList();
    }

    public Map<String, Object> getGroupEnrollmentById(UUID id) {
        Map<String, Object> ge = camelizeMap(jdbc.queryForMap(
                "SELECT ge.*, gp.title AS promotion_title, gp.discount_percent, gp.min_participants, gp.max_participants " +
                        "FROM group_enrollments ge JOIN group_promotions gp ON gp.id = ge.promotion_id WHERE ge.id = ?",
                id
        ));
        List<Map<String, Object>> members = jdbc.queryForList(
                "SELECT invited_email, member_role, status, accepted_consent, joined_at_utc FROM group_enrollment_members WHERE group_enrollment_id = ? ORDER BY member_role DESC, invited_email ASC",
                id
        ).stream().map(this::camelizeMap).toList();
        ge.put("members", members);
        return ge;
    }

    public List<Map<String, Object>> listReviewsForOrganization(UUID organizationId) {
        return jdbc.queryForList(
                "SELECT r.id, r.organization_id, r.branch_id, r.rating_overall, r.rating_cleanliness, r.rating_equipment, " +
                        "r.rating_attention, r.rating_value, r.comment, r.verified_eligibility_source, r.status, " +
                        "r.gym_reply, r.replied_by, r.replied_at_utc, r.created_at_utc " +
                        "FROM gym_reviews r WHERE r.organization_id = ? AND r.status = 'PUBLICADA' ORDER BY r.created_at_utc DESC",
                organizationId
        ).stream().map(this::camelizeMap).toList();
    }

    @Transactional
    public Map<String, Object> createVerifiedReview(String actorEmail, UUID organizationId, UUID branchId,
                                                    int overall, int cleanliness, int equipment,
                                                    int attention, int value, String comment) {
        Account account = accountRepository.findByEmailIgnoreCase(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada."));
        if (isPrivilegedStaff(account)) {
            throw new AccessDeniedException("El personal o administración no puede autocalificar gimnasios desde cuentas operativas.");
        }
        UUID clientId = memberWorkspaceService.findOwnClient(actorEmail)
                .orElseThrow(() -> new AccessDeniedException("Solo socios con ficha vinculada y vínculo verificable pueden calificar este gimnasio."));

        Long membershipCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM memberships WHERE client_id = ?", Long.class, clientId
        );
        Long attendanceCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM member_attendance WHERE client_id = ?", Long.class, clientId
        );

        String eligibility;
        if (membershipCount != null && membershipCount > 0) {
            eligibility = "ACTIVE_OR_PAST_MEMBERSHIP";
        } else if (attendanceCount != null && attendanceCount > 0) {
            eligibility = "VERIFIED_ATTENDANCE";
        } else {
            throw new AccessDeniedException("Debes contar con al menos una membresía o asistencia verificada para emitir una reseña.");
        }

        List<Map<String, Object>> dup = jdbc.queryForList(
                "SELECT id FROM gym_reviews WHERE organization_id = ? AND author_account_id = ?",
                organizationId, account.getId()
        );
        if (!dup.isEmpty()) {
            throw new ConflictException("Ya registraste una reseña verificada para este gimnasio.");
        }
        if (comment == null || comment.trim().length() < 10) {
            throw new IllegalArgumentException("El comentario debe describir tu experiencia con al menos 10 caracteres.");
        }

        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO gym_reviews (id, organization_id, branch_id, author_account_id, rating_overall, rating_cleanliness, rating_equipment, rating_attention, rating_value, comment, verified_eligibility_source, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PUBLICADA')",
                id, organizationId, branchId, account.getId(), overall, cleanliness, equipment, attention, value,
                comment.trim(), eligibility
        );
        return camelizeMap(jdbc.queryForMap("SELECT * FROM gym_reviews WHERE id = ?", id));
    }

    @Transactional
    public Map<String, Object> replyToReview(String actorEmail, UUID organizationId, UUID reviewId, String replyText) {
        if (replyText == null || replyText.trim().length() < 5) {
            throw new IllegalArgumentException("La respuesta oficial debe tener al menos 5 caracteres.");
        }
        resolveOrgIdForActor(actorEmail, organizationId);
        int updated = jdbc.update(
                "UPDATE gym_reviews SET gym_reply = ?, replied_by = ?, replied_at_utc = NOW(), updated_at_utc = NOW() WHERE id = ? AND organization_id = ?",
                replyText.trim(), actorEmail, reviewId, organizationId
        );
        if (updated == 0) {
            throw new ResourceNotFoundException("Reseña no encontrada para esta empresa.");
        }
        return camelizeMap(jdbc.queryForMap("SELECT * FROM gym_reviews WHERE id = ?", reviewId));
    }

    public Map<String, Object> getPhysicalProfile(String actorEmail) {
        Account account = accountRepository.findByEmailIgnoreCase(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada."));
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM member_physical_profiles WHERE account_id = ?",
                account.getId()
        );
        if (rows.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("configured", false);
            empty.put("bmiDisclaimer", "El perfil físico e IMC son completamente opcionales y meramente informativos (OMS).");
            return empty;
        }
        Map<String, Object> dto = camelizeMap(rows.get(0));
        dto.put("configured", true);
        dto.put("bmiDisclaimer", "Indicador referencial e informativo basado en criterios OMS. No distingue masa muscular de grasa corporal y no constituye diagnóstico médico.");
        return dto;
    }

    @Transactional
    public Map<String, Object> upsertPhysicalProfile(String actorEmail, BigDecimal heightCm, BigDecimal weightKg,
                                                     String primaryGoal, String experienceLevel,
                                                     Integer weeklyDays, String injuriesOrNotes, boolean consentSaved) {
        Account account = accountRepository.findByEmailIgnoreCase(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada."));
        if (weeklyDays != null && (weeklyDays < 1 || weeklyDays > 7)) {
            throw new IllegalArgumentException("Los días disponibles por semana deben estar entre 1 y 7.");
        }
        SaasCalculations.EducationalBmiResult bmi = SaasCalculations.calculateEducationalBmi(heightCm, weightKg);
        jdbc.update(
                "INSERT INTO member_physical_profiles (account_id, height_cm, weight_kg, primary_goal, experience_level, weekly_availability_days, injuries_or_notes, bmi_value, bmi_classification, consent_saved, updated_at_utc) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW()) " +
                        "ON CONFLICT (account_id) DO UPDATE SET " +
                        "height_cm = EXCLUDED.height_cm, weight_kg = EXCLUDED.weight_kg, primary_goal = EXCLUDED.primary_goal, " +
                        "experience_level = EXCLUDED.experience_level, weekly_availability_days = EXCLUDED.weekly_availability_days, " +
                        "injuries_or_notes = EXCLUDED.injuries_or_notes, bmi_value = EXCLUDED.bmi_value, " +
                        "bmi_classification = EXCLUDED.bmi_classification, consent_saved = EXCLUDED.consent_saved, updated_at_utc = NOW()",
                account.getId(), heightCm, weightKg, primaryGoal, experienceLevel, weeklyDays, injuriesOrNotes,
                bmi.bmiValue(), bmi.classification(), consentSaved
        );
        return getPhysicalProfile(actorEmail);
    }

    @Transactional
    public void deletePhysicalProfile(String actorEmail) {
        Account account = accountRepository.findByEmailIgnoreCase(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada."));
        jdbc.update("DELETE FROM member_physical_profiles WHERE account_id = ?", account.getId());
    }

    public List<Map<String, Object>> listWeeklyPlans(String actorEmail) {
        Account account = accountRepository.findByEmailIgnoreCase(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada."));
        return jdbc.queryForList(
                "SELECT id, title, goal, level, week_start_date, days_json, is_active, created_at_utc, updated_at_utc " +
                        "FROM weekly_training_plans WHERE account_id = ? AND is_active = TRUE ORDER BY updated_at_utc DESC",
                account.getId()
        ).stream().map(this::camelizeMap).toList();
    }

    @Transactional
    public Map<String, Object> saveWeeklyPlan(String actorEmail, UUID planId, String title, String goal,
                                              String level, LocalDate weekStartDate, String daysJson) {
        Account account = accountRepository.findByEmailIgnoreCase(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada."));
        UUID id = planId != null ? planId : UUID.randomUUID();
        LocalDate start = weekStartDate != null ? weekStartDate : LocalDate.now();
        jdbc.update(
                "INSERT INTO weekly_training_plans (id, account_id, title, goal, level, week_start_date, days_json, is_active, created_at_utc, updated_at_utc) " +
                        "VALUES (?, ?, ?, ?, ?, ?, COALESCE(?, '[]'), TRUE, NOW(), NOW()) " +
                        "ON CONFLICT (id) DO UPDATE SET " +
                        "title = EXCLUDED.title, goal = EXCLUDED.goal, level = EXCLUDED.level, " +
                        "week_start_date = EXCLUDED.week_start_date, days_json = EXCLUDED.days_json, updated_at_utc = NOW()",
                id, account.getId(), title, goal, level != null ? level : "PRINCIPIANTE", Date.valueOf(start), daysJson
        );
        return camelizeMap(jdbc.queryForMap(
                "SELECT id, title, goal, level, week_start_date, days_json, is_active, created_at_utc, updated_at_utc FROM weekly_training_plans WHERE id = ? AND account_id = ?",
                id, account.getId()
        ));
    }

    @Transactional
    public void deactivateWeeklyPlan(String actorEmail, UUID planId) {
        Account account = accountRepository.findByEmailIgnoreCase(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada."));
        jdbc.update("UPDATE weekly_training_plans SET is_active = FALSE, updated_at_utc = NOW() WHERE id = ? AND account_id = ?",
                planId, account.getId());
    }

    public Map<String, Object> getNotificationPreferences(String actorEmail) {
        Account account = accountRepository.findByEmailIgnoreCase(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada."));
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM notification_preferences WHERE account_id = ?",
                account.getId()
        );
        if (rows.isEmpty()) {
            Map<String, Object> defaults = new LinkedHashMap<>();
            defaults.put("accountId", account.getId());
            defaults.put("channelPushOperational", true);
            defaults.put("channelPushReminders", true);
            defaults.put("channelPushPromotions", false);
            defaults.put("channelEmailOperational", true);
            defaults.put("channelEmailPromotions", false);
            defaults.put("channelWhatsappOperational", true);
            defaults.put("channelWhatsappPromotions", false);
            return defaults;
        }
        return camelizeMap(rows.get(0));
    }

    @Transactional
    public Map<String, Object> updateNotificationPreferences(String actorEmail, boolean pushOps, boolean pushRem,
                                                             boolean pushPromo, boolean emailOps, boolean emailPromo,
                                                             boolean waOps, boolean waPromo) {
        Account account = accountRepository.findByEmailIgnoreCase(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada."));
        jdbc.update(
                "INSERT INTO notification_preferences (account_id, channel_push_operational, channel_push_reminders, channel_push_promotions, channel_email_operational, channel_email_promotions, channel_whatsapp_operational, channel_whatsapp_promotions, updated_at_utc) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW()) " +
                        "ON CONFLICT (account_id) DO UPDATE SET " +
                        "channel_push_operational = EXCLUDED.channel_push_operational, channel_push_reminders = EXCLUDED.channel_push_reminders, " +
                        "channel_push_promotions = EXCLUDED.channel_push_promotions, channel_email_operational = EXCLUDED.channel_email_operational, " +
                        "channel_email_promotions = EXCLUDED.channel_email_promotions, channel_whatsapp_operational = EXCLUDED.channel_whatsapp_operational, " +
                        "channel_whatsapp_promotions = EXCLUDED.channel_whatsapp_promotions, updated_at_utc = NOW()",
                account.getId(), pushOps, pushRem, pushPromo, emailOps, emailPromo, waOps, waPromo
        );
        return getNotificationPreferences(actorEmail);
    }

    public List<Map<String, Object>> listActiveLegalDocuments() {
        return jdbc.queryForList(
                "SELECT id, document_code, version, title, summary, content_markdown, effective_date FROM legal_document_versions WHERE is_active = TRUE ORDER BY document_code ASC"
        ).stream().map(this::camelizeMap).toList();
    }

    public List<Map<String, Object>> listAccountLegalAcceptances(String actorEmail) {
        Account account = accountRepository.findByEmailIgnoreCase(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada."));
        return jdbc.queryForList(
                "SELECT id, document_code, document_version, accepted, purpose, accepted_at_utc FROM user_legal_acceptances WHERE account_id = ? ORDER BY accepted_at_utc DESC",
                account.getId()
        ).stream().map(this::camelizeMap).toList();
    }

    @Transactional
    public Map<String, Object> recordLegalAcceptance(String actorEmail, String documentCode, String version,
                                                     boolean accepted, String purpose, String ip, String ua) {
        Account account = accountRepository.findByEmailIgnoreCase(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada."));
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO user_legal_acceptances (id, account_id, subject_email, document_code, document_version, accepted, purpose, ip_address, user_agent) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, account.getId(), account.getEmail().toLowerCase(Locale.ROOT),
                documentCode.trim().toUpperCase(Locale.ROOT), version.trim(), accepted,
                purpose != null ? purpose : "PLATFORM_ACCESS", ip, ua
        );
        return camelizeMap(jdbc.queryForMap(
                "SELECT id, document_code, document_version, accepted, purpose, accepted_at_utc FROM user_legal_acceptances WHERE id = ?",
                id
        ));
    }

    public Map<String, Object> getLatestAppRelease(String platform, String channel, Integer currentVersionCode) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM app_releases WHERE UPPER(platform) = UPPER(?) AND UPPER(channel) = UPPER(?) AND is_active = TRUE ORDER BY version_code DESC LIMIT 1",
                platform != null ? platform : "ANDROID",
                channel != null ? channel : "PRODUCTION"
        );
        if (rows.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("available", false);
            return empty;
        }
        Map<String, Object> dto = camelizeMap(rows.get(0));
        int latestCode = ((Number) dto.get("versionCode")).intValue();
        int minSupported = ((Number) dto.get("minSupportedVersionCode")).intValue();
        boolean updateAvailable = currentVersionCode != null && latestCode > currentVersionCode;
        boolean updateMandatory = Boolean.TRUE.equals(dto.get("isMandatory"))
                || (currentVersionCode != null && currentVersionCode < minSupported);
        dto.put("available", true);
        dto.put("updateAvailable", updateAvailable);
        dto.put("updateRequired", updateMandatory);
        return dto;
    }

    private UUID resolveOrgIdForActor(String actorEmail, UUID requestedOrgId) {
        Account account = accountRepository.findByEmailIgnoreCase(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada."));
        boolean isSuperAdmin = account.getRoles().stream().anyMatch(r -> "SUPER_ADMIN".equalsIgnoreCase(r) || "ADMIN".equalsIgnoreCase(r));
        if (isSuperAdmin && requestedOrgId != null) {
            return requestedOrgId;
        }
        if (account.getOrganizationId() != null) {
            if (requestedOrgId != null && !account.getOrganizationId().equals(requestedOrgId) && !isSuperAdmin) {
                throw new AccessDeniedException("No tienes autorización para administrar recursos de otra empresa.");
            }
            return account.getOrganizationId();
        }
        return UUID.fromString("11111111-1111-4111-8111-111111111111");
    }

    private boolean isPrivilegedStaff(Account account) {
        return account.getRoles().stream().anyMatch(r -> OWNER_STAFF_ROLES.contains(r.toUpperCase(Locale.ROOT)));
    }

    private Map<String, Object> camelizeMap(Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            out.put(toCamelCase(e.getKey()), e.getValue());
        }
        return out;
    }

    private String toCamelCase(String snake) {
        StringBuilder sb = new StringBuilder();
        boolean upperNext = false;
        for (char c : snake.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c == '_') {
                upperNext = true;
            } else if (upperNext) {
                sb.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
