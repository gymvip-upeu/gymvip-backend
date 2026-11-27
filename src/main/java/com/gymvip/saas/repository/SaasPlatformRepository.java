package com.gymvip.saas.repository;

import com.gymvip.entity.Account;
import com.gymvip.exception.ConflictException;
import com.gymvip.exception.ResourceNotFoundException;
import com.gymvip.repository.AccountRepository;
import com.gymvip.service.AuditService;
import com.gymvip.saas.domain.SaasCalculations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class SaasPlatformRepository {

    private static final Set<String> VALID_LICENSE_STATUSES = Set.of(
            "TRIAL", "ACTIVA", "POR_VENCER", "VENCIDA", "SUSPENDIDA", "CANCELADA", "DEMO"
    );
    private static final Set<String> VALID_REQUEST_STATUSES = Set.of(
            "RECIBIDA", "EN_REVISION", "OBSERVADA", "APROBADA", "INVITACION_ENVIADA", "ACTIVADA", "RECHAZADA", "ARCHIVADA"
    );
    private static final Set<String> VALID_LEDGER_STATUSES = Set.of(
            "CALCULADA", "FACTURADA", "COBRADA", "ANULADA", "REEMBOLSADA"
    );

    private final JdbcTemplate jdbc;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public SaasPlatformRepository(JdbcTemplate jdbc,
                                  AccountRepository accountRepository,
                                  PasswordEncoder passwordEncoder, AuditService audit) {
        this.jdbc = jdbc;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    public Map<String, Object> getGlobalPlatformMetrics() {
        List<Map<String, Object>> licenseRows = jdbc.queryForList(
                "SELECT l.status, COUNT(*) AS cnt, COALESCE(SUM(p.monthly_fee_pen), 0) AS mrr_pen " +
                        "FROM saas_licenses l JOIN saas_plans p ON p.id = l.saas_plan_id " +
                        "GROUP BY l.status"
        );
        Map<String, Long> licensesByStatus = new LinkedHashMap<>();
        BigDecimal platformMrrPen = BigDecimal.ZERO;
        long totalLicensedOrgs = 0;
        for (Map<String, Object> row : licenseRows) {
            String st = String.valueOf(row.get("status"));
            long cnt = ((Number) row.get("cnt")).longValue();
            licensesByStatus.put(st, cnt);
            totalLicensedOrgs += cnt;
            if ("ACTIVA".equals(st) || "POR_VENCER".equals(st)) {
                platformMrrPen = platformMrrPen.add(new BigDecimal(String.valueOf(row.get("mrr_pen"))));
            }
        }

        Map<String, Object> commissionSummary = jdbc.queryForMap(
                "SELECT " +
                        "COALESCE(SUM(CASE WHEN ledger_status IN ('CALCULADA','FACTURADA','COBRADA') THEN gross_amount_pen ELSE 0 END), 0) AS gyms_gross_volume_pen, " +
                        "COALESCE(SUM(CASE WHEN ledger_status IN ('CALCULADA','FACTURADA','COBRADA') THEN gateway_fee_pen ELSE 0 END), 0) AS gateway_fees_pen, " +
                        "COALESCE(SUM(CASE WHEN ledger_status IN ('CALCULADA','FACTURADA','COBRADA') THEN platform_commission_pen ELSE 0 END), 0) AS platform_commissions_pen, " +
                        "COALESCE(SUM(CASE WHEN ledger_status IN ('CALCULADA','FACTURADA','COBRADA') THEN gym_net_amount_pen ELSE 0 END), 0) AS gyms_net_volume_pen " +
                        "FROM commission_ledger"
        );

        Long pendingRequests = jdbc.queryForObject(
                "SELECT COUNT(*) FROM organization_requests WHERE status IN ('RECIBIDA','EN_REVISION','OBSERVADA')",
                Long.class
        );
        Long totalActiveBranches = jdbc.queryForObject(
                "SELECT COUNT(*) FROM branches WHERE status = 'ACTIVO'",
                Long.class
        );
        Long totalGymsMembershipsGrossCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment_records",
                Long.class
        );
        BigDecimal gymsCashCollectedPen = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount_pen), 0) FROM payment_records",
                BigDecimal.class
        );

        BigDecimal platformCommissionsPen = new BigDecimal(String.valueOf(commissionSummary.get("platform_commissions_pen")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("revenueSeparationNotice",
                "Métricas estrictamente diferenciadas: platformRevenue* pertenece a GYM VIP SaaS; gymsVolume* pertenece a los gimnasios clientes.");
        result.put("platformMrrPen", platformMrrPen);
        result.put("platformCommissionsPen", platformCommissionsPen);
        result.put("platformTotalEstimatedMonthlyRevenuePen", platformMrrPen.add(platformCommissionsPen));
        result.put("gymsDigitalGrossVolumePen", new BigDecimal(String.valueOf(commissionSummary.get("gyms_gross_volume_pen"))));
        result.put("gymsGatewayFeesPen", new BigDecimal(String.valueOf(commissionSummary.get("gateway_fees_pen"))));
        result.put("gymsDigitalNetVolumePen", new BigDecimal(String.valueOf(commissionSummary.get("gyms_net_volume_pen"))));
        result.put("gymsRecordedPaymentsCount", totalGymsMembershipsGrossCount != null ? totalGymsMembershipsGrossCount : 0L);
        result.put("gymsRecordedPaymentsVolumePen", gymsCashCollectedPen != null ? gymsCashCollectedPen : BigDecimal.ZERO);
        result.put("totalLicensedOrganizations", totalLicensedOrgs);
        result.put("licensesByStatus", licensesByStatus);
        result.put("pendingCompanyRequests", pendingRequests != null ? pendingRequests : 0L);
        result.put("totalActiveBranches", totalActiveBranches != null ? totalActiveBranches : 0L);
        return result;
    }

    public List<Map<String, Object>> listSaasPlans() {
        return jdbc.queryForList(
                "SELECT id, code, name, description, monthly_fee_pen, annual_fee_pen, max_branches, max_users, transaction_commission_pct, included_modules_json, is_active " +
                        "FROM saas_plans ORDER BY monthly_fee_pen ASC"
        ).stream().map(this::camelizeMap).toList();
    }

    public List<Map<String, Object>> listLicenses() {
        return jdbc.queryForList(
                "SELECT l.id, l.organization_id, o.code AS organization_code, o.name AS organization_name, o.tax_id, " +
                        "p.code AS plan_code, p.name AS plan_name, p.monthly_fee_pen, l.status, l.started_at, l.expires_at, " +
                        "l.auto_renew, l.max_branches, l.max_users, l.enabled_modules_json, l.allow_data_export, l.notes, l.updated_by, l.updated_at " +
                        "FROM saas_licenses l JOIN organizations o ON o.id = l.organization_id JOIN saas_plans p ON p.id = l.saas_plan_id " +
                        "ORDER BY l.updated_at DESC"
        ).stream().map(this::camelizeMap).toList();
    }

    @Transactional
    public Map<String, Object> upsertLicense(UUID organizationId, String planCode, String status, LocalDate expiresAt,
                                             Integer maxBranches, Integer maxUsers, Boolean autoRenew,
                                             String notes, String actorEmail) {
        String normStatus = normalizeInSet(status, VALID_LICENSE_STATUSES, "Estado de licencia inválido: " + status);
        Map<String, Object> plan = jdbc.queryForMap(
                "SELECT id, max_branches, max_users, included_modules_json FROM saas_plans WHERE UPPER(code) = UPPER(?)",
                planCode
        );
        UUID planId = (UUID) plan.get("id");
        int effBranches = maxBranches != null && maxBranches > 0 ? maxBranches : ((Number) plan.get("max_branches")).intValue();
        int effUsers = maxUsers != null && maxUsers > 0 ? maxUsers : ((Number) plan.get("max_users")).intValue();
        LocalDate effExpires = expiresAt != null ? expiresAt : LocalDate.now().plusDays("TRIAL".equals(normStatus) ? 14 : 30);

        jdbc.update(
                "INSERT INTO saas_licenses (id, organization_id, saas_plan_id, status, started_at, expires_at, auto_renew, max_branches, max_users, enabled_modules_json, allow_data_export, notes, updated_by, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, CURRENT_DATE, ?, ?, ?, ?, ?, TRUE, ?, ?, NOW(), NOW()) " +
                        "ON CONFLICT (organization_id) DO UPDATE SET " +
                        "saas_plan_id = EXCLUDED.saas_plan_id, status = EXCLUDED.status, expires_at = EXCLUDED.expires_at, " +
                        "auto_renew = EXCLUDED.auto_renew, max_branches = EXCLUDED.max_branches, max_users = EXCLUDED.max_users, " +
                        "allow_data_export = TRUE, notes = EXCLUDED.notes, updated_by = EXCLUDED.updated_by, updated_at = NOW()",
                UUID.randomUUID(), organizationId, planId, normStatus, Date.valueOf(effExpires),
                autoRenew != null ? autoRenew : Boolean.TRUE, effBranches, effUsers,
                String.valueOf(plan.get("included_modules_json")), notes, actorEmail
        );
        return listLicenses().stream()
                .filter(l -> String.valueOf(organizationId).equals(String.valueOf(l.get("organizationId"))))
                .findFirst()
                .orElseThrow();
    }

    public List<Map<String, Object>> listCommissionRules() {
        return jdbc.queryForList(
                "SELECT id, organization_id, operation_type, commission_type, percentage_rate, fixed_fee_pen, gateway_passthrough_pct, applies_only_settled, is_active, created_by, created_at " +
                        "FROM commission_rules ORDER BY created_at DESC"
        ).stream().map(this::camelizeMap).toList();
    }

    @Transactional
    public Map<String, Object> createCommissionRule(UUID organizationId, String operationType, String commissionType,
                                                    BigDecimal percentageRate, BigDecimal fixedFeePen,
                                                    BigDecimal gatewayPassthroughPct, String actorEmail) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO commission_rules (id, organization_id, operation_type, commission_type, percentage_rate, fixed_fee_pen, gateway_passthrough_pct, applies_only_settled, is_active, created_by) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, TRUE, ?)",
                id, organizationId, operationType.trim().toUpperCase(Locale.ROOT),
                commissionType != null ? commissionType.trim().toUpperCase(Locale.ROOT) : "PERCENTAGE",
                percentageRate, fixedFeePen != null ? fixedFeePen : BigDecimal.ZERO,
                gatewayPassthroughPct != null ? gatewayPassthroughPct : new BigDecimal("0.0344"),
                actorEmail
        );
        return camelizeMap(jdbc.queryForMap(
                "SELECT id, organization_id, operation_type, commission_type, percentage_rate, fixed_fee_pen, gateway_passthrough_pct, applies_only_settled, is_active, created_by, created_at FROM commission_rules WHERE id = ?",
                id
        ));
    }

    public List<Map<String, Object>> listCommissionLedger(UUID organizationId, String status) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT c.id, c.organization_id, o.name AS organization_name, c.operation_type, c.source_operation_id, c.source_reference, " +
                        "c.payment_status, c.gross_amount_pen, c.gateway_fee_pen, c.platform_commission_pen, c.gym_net_amount_pen, " +
                        "c.ledger_status, c.settled_period, c.notes, c.created_at_utc, c.updated_at_utc " +
                        "FROM commission_ledger c JOIN organizations o ON o.id = c.organization_id WHERE 1=1 "
        );
        if (organizationId != null) {
            sql.append("AND c.organization_id = ? ");
            params.add(organizationId);
        }
        if (status != null && !status.isBlank()) {
            sql.append("AND UPPER(c.ledger_status) = UPPER(?) ");
            params.add(status.trim());
        }
        sql.append("ORDER BY c.created_at_utc DESC LIMIT 150");
        return jdbc.queryForList(sql.toString(), params.toArray()).stream().map(this::camelizeMap).toList();
    }

    @Transactional
    public Map<String, Object> recordSettledCommission(UUID organizationId, String operationType, UUID sourceOperationId,
                                                       String sourceReference, String paymentStatus,
                                                       BigDecimal grossAmountPen, String settledPeriod, String notes) {
        String op = operationType != null ? operationType.trim().toUpperCase(Locale.ROOT) : "MEMBERSHIP";
        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT id FROM commission_ledger WHERE operation_type = ? AND source_operation_id = ?",
                op, sourceOperationId
        );
        if (!existing.isEmpty()) {
            return camelizeMap(jdbc.queryForMap(
                    "SELECT id, organization_id, operation_type, source_operation_id, source_reference, payment_status, gross_amount_pen, gateway_fee_pen, platform_commission_pen, gym_net_amount_pen, ledger_status, settled_period, notes FROM commission_ledger WHERE id = ?",
                    existing.get(0).get("id")
            ));
        }

        List<Map<String, Object>> rules = jdbc.queryForList(
                "SELECT id, commission_type, percentage_rate, fixed_fee_pen, gateway_passthrough_pct " +
                        "FROM commission_rules WHERE is_active = TRUE AND UPPER(operation_type) = UPPER(?) " +
                        "AND (organization_id = ? OR organization_id IS NULL) " +
                        "ORDER BY CASE WHEN organization_id IS NOT NULL THEN 0 ELSE 1 END, created_at DESC LIMIT 1",
                op, organizationId
        );
        UUID ruleId = null;
        String commType = "PERCENTAGE";
        BigDecimal rate = new BigDecimal("0.0300");
        BigDecimal fixed = BigDecimal.ZERO;
        BigDecimal gwPct = new BigDecimal("0.0344");
        if (!rules.isEmpty()) {
            Map<String, Object> r = rules.get(0);
            ruleId = (UUID) r.get("id");
            commType = String.valueOf(r.get("commission_type"));
            rate = new BigDecimal(String.valueOf(r.get("percentage_rate")));
            fixed = new BigDecimal(String.valueOf(r.get("fixed_fee_pen")));
            gwPct = new BigDecimal(String.valueOf(r.get("gateway_passthrough_pct")));
        }

        SaasCalculations.CommissionBreakdown breakdown = SaasCalculations.calculateCommission(
                grossAmountPen, paymentStatus, commType, rate, fixed, gwPct
        );

        UUID id = UUID.randomUUID();
        String period = settledPeriod != null && !settledPeriod.isBlank() ? settledPeriod : YearMonth.now().toString();
        jdbc.update(
                "INSERT INTO commission_ledger (id, organization_id, operation_type, source_operation_id, source_reference, payment_status, gross_amount_pen, gateway_fee_pen, platform_commission_pen, gym_net_amount_pen, applied_rule_id, ledger_status, settled_period, notes) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'CALCULADA', ?, ?)",
                id, organizationId, op, sourceOperationId, sourceReference, paymentStatus.trim().toUpperCase(Locale.ROOT),
                breakdown.grossAmountPen(), breakdown.gatewayFeePen(), breakdown.platformCommissionPen(), breakdown.gymNetAmountPen(),
                ruleId, period, notes
        );
        return camelizeMap(jdbc.queryForMap(
                "SELECT id, organization_id, operation_type, source_operation_id, source_reference, payment_status, gross_amount_pen, gateway_fee_pen, platform_commission_pen, gym_net_amount_pen, ledger_status, settled_period, notes FROM commission_ledger WHERE id = ?",
                id
        ));
    }

    @Transactional
    public Map<String, Object> updateCommissionLedgerStatus(UUID id, String newStatus, String notes) {
        String norm = normalizeInSet(newStatus, VALID_LEDGER_STATUSES, "Estado de liquidación inválido: " + newStatus);
        jdbc.update(
                "UPDATE commission_ledger SET ledger_status = ?, notes = COALESCE(?, notes), updated_at_utc = NOW() WHERE id = ?",
                norm, notes, id
        );
        return camelizeMap(jdbc.queryForMap(
                "SELECT id, organization_id, operation_type, source_operation_id, source_reference, payment_status, gross_amount_pen, gateway_fee_pen, platform_commission_pen, gym_net_amount_pen, ledger_status, settled_period, notes FROM commission_ledger WHERE id = ?",
                id
        ));
    }

    @Transactional
    public Map<String, Object> submitCompanyRequest(String businessName, String commercialName, String ruc,
                                                    String city, String district, String address,
                                                    String contactName, String contactEmail, String contactPhone,
                                                    int branchesCount, int estimatedMembers, String requestedPlanCode,
                                                    String message, boolean legalAccepted, String legalVersion) {
        if (!legalAccepted) {
            throw new IllegalArgumentException("Debes aceptar explícitamente la Política de Privacidad y Términos de Servicio SaaS.");
        }
        if (ruc == null || !ruc.trim().matches("\\d{11}")) {
            throw new IllegalArgumentException("El RUC debe tener exactamente 11 dígitos numéricos.");
        }
        String tracking = "GYM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 28).toUpperCase(Locale.ROOT);
        UUID id = UUID.randomUUID();
        String normPlan = requestedPlanCode != null && !requestedPlanCode.isBlank()
                ? requestedPlanCode.trim().toUpperCase(Locale.ROOT)
                : "PRO";
        jdbc.update(
                "INSERT INTO organization_requests (id, tracking_code, business_name, commercial_name, ruc, city, district, address, contact_name, contact_email, contact_phone, branches_count, estimated_members, requested_plan_code, message, status, legal_accepted, legal_version) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'RECIBIDA', TRUE, ?)",
                id, tracking, businessName.trim(), commercialName, ruc.trim(), city.trim(), district.trim(),
                address, contactName.trim(), contactEmail.trim().toLowerCase(Locale.ROOT), contactPhone.trim(),
                Math.max(1, branchesCount), Math.max(1, estimatedMembers), normPlan, message,
                legalVersion != null && !legalVersion.isBlank() ? legalVersion : "v2.0-2026"
        );
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("trackingCode", tracking);
        out.put("status", "RECIBIDA");
        out.put("businessName", businessName.trim());
        out.put("requestedPlanCode", normPlan);
        return out;
    }

    public Optional<Map<String, Object>> trackCompanyRequest(String trackingCode) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT tracking_code, status, created_at_utc, updated_at_utc " +
                        "FROM organization_requests WHERE UPPER(tracking_code) = UPPER(?)",
                trackingCode.trim()
        );
        return rows.stream().findFirst().map(this::camelizeMap);
    }

    public List<Map<String, Object>> listCompanyRequests(String status) {
        String query = """
                SELECT r.*, i.email_status AS invitation_status FROM organization_requests r
                LEFT JOIN company_activation_invitations i ON i.request_id = r.id
                """;
        if (status != null && !status.isBlank()) {
            return jdbc.queryForList(query + " WHERE UPPER(r.status) = UPPER(?) ORDER BY r.created_at_utc DESC",
                    status.trim()).stream().map(this::camelizeMap).toList();
        }
        return jdbc.queryForList(query + " ORDER BY r.created_at_utc DESC")
                .stream().map(this::camelizeMap).toList();
    }

    @Transactional
    public Map<String, Object> reviewCompanyRequest(UUID requestId, String status, String notes, String reviewerEmail) {
        String norm = normalizeInSet(status, VALID_REQUEST_STATUSES, "Estado de solicitud inválido: " + status);
        if (!Set.of("EN_REVISION", "OBSERVADA", "RECHAZADA", "ARCHIVADA").contains(norm)) {
            throw new IllegalArgumentException("Utiliza la acción de aprobación para aprovisionar la empresa.");
        }
        int updated = jdbc.update(
                "UPDATE organization_requests SET status = ?, review_notes = ?, reviewed_by = ?, updated_at_utc = NOW() WHERE id = ? AND approved_organization_id IS NULL",
                norm, notes, reviewerEmail, requestId
        );
        if (updated == 0) throw new ConflictException("La solicitud no admite una nueva revisión.");
        return camelizeMap(jdbc.queryForMap("SELECT * FROM organization_requests WHERE id = ?", requestId));
    }

    @Transactional
    public Map<String, Object> recordCompanyVerification(UUID requestId, String fiscalStatus, String fiscalCondition,
            String businessName, String representativeEvidence, boolean contactConfirmed, String reviewer) {
        if (jdbc.update("""
                UPDATE organization_requests SET fiscal_status=?, fiscal_condition=?, verified_business_name=?,
                    representative_evidence=?, verified_contact_at=CASE WHEN ? THEN NOW() ELSE NULL END,
                    fiscal_reviewed_at=NOW(), fiscal_reviewed_by=?, updated_at_utc=NOW()
                WHERE id=? AND approved_organization_id IS NULL AND status IN ('RECIBIDA','EN_REVISION','OBSERVADA')
                """, fiscalStatus, fiscalCondition, businessName.trim(), representativeEvidence.trim(), contactConfirmed,
                reviewer, requestId) != 1) {
            throw new ConflictException("La solicitud no admite registrar una nueva validación.");
        }
        audit.recordEvent(reviewer, "REVIEW_COMPANY_IDENTITY", "OrganizationRequest", requestId.toString(),
                "Consulta SUNAT registrada y representación revisada.");
        return camelizeMap(jdbc.queryForMap("SELECT * FROM organization_requests WHERE id=?", requestId));
    }

    private boolean canApproveVerifiedCompany(Map<String, Object> request) {
        if (!"ACTIVO".equals(request.get("fiscal_status")) || !"HABIDO".equals(request.get("fiscal_condition"))
                || request.get("verified_contact_at") == null || request.get("fiscal_reviewed_at") == null
                || request.get("representative_evidence") == null || request.get("verified_business_name") == null) {
            return false;
        }
        return String.valueOf(request.get("business_name")).trim().replaceAll("\\s+", " ")
                .equalsIgnoreCase(String.valueOf(request.get("verified_business_name")).trim().replaceAll("\\s+", " "));
    }

    @Transactional
    public Map<String, Object> approveAndProvisionCompanyRequest(UUID requestId, String reviewerEmail, boolean startTrial) {
        Map<String, Object> req = jdbc.queryForList("SELECT * FROM organization_requests WHERE id = ? FOR UPDATE", requestId)
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada."));
        if (req.get("approved_organization_id") != null) {
            return Map.of("requestId", requestId, "organizationId", req.get("approved_organization_id"), "status", req.get("status"));
        }
        if (!Set.of("RECIBIDA", "EN_REVISION", "OBSERVADA").contains(req.get("status"))) {
            throw new ConflictException("La solicitud debe estar en revisión para ser aprobada.");
        }
        if (!canApproveVerifiedCompany(req)) {
            throw new ConflictException("Registra el RUC activo y habido, la razón social coincidente, la representación y el contacto verificado.");
        }
        String contactEmail = String.valueOf(req.get("contact_email")).trim().toLowerCase(Locale.ROOT);
        if (accountRepository.existsByEmailIgnoreCase(contactEmail)) {
            throw new ConflictException("El correo ya pertenece a una cuenta. Revisa el contacto antes de aprobar.");
        }
        String ruc = String.valueOf(req.get("ruc"));
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, "company-request:" + ruc);
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM organizations WHERE tax_id = ?)", Boolean.class, ruc))) {
            throw new ConflictException("Ya existe una empresa con ese RUC.");
        }
        String name = req.get("commercial_name") != null && !String.valueOf(req.get("commercial_name")).isBlank()
                ? String.valueOf(req.get("commercial_name"))
                : String.valueOf(req.get("business_name"));
        String slugBase = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slugBase.isBlank()) slugBase = "gym";
        if (slugBase.length() > 50) slugBase = slugBase.substring(0, 50).replaceAll("-$", "");
        String orgCode = slugBase + "-" + UUID.randomUUID().toString().substring(0, 4);
        UUID orgId = UUID.randomUUID();
        String planCode = String.valueOf(req.get("requested_plan_code"));

        jdbc.update(
                "INSERT INTO organizations (id, code, name, tax_id, description, plan_tier, status, is_published) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVO', FALSE)",
                orgId, orgCode, name, String.valueOf(req.get("ruc")),
                "Gimnasio afiliado a la red GYM VIP (" + req.get("city") + " - " + req.get("district") + ")",
                planCode
        );

        upsertLicense(orgId, planCode, startTrial ? "TRIAL" : "ACTIVA", null,
                ((Number) req.get("branches_count")).intValue(), null, true,
                "Aprovisionamiento automático tras aprobación de solicitud " + req.get("tracking_code"), reviewerEmail);

        jdbc.update(
                "INSERT INTO organization_commercial_profiles (organization_id, slug, headline, tagline, about_text, publish_status, updated_by) " +
                        "VALUES (?, ?, ?, ?, ?, 'BORRADOR', ?)",
                orgId, orgCode, name, "Sede en " + req.get("district") + ", " + req.get("city"),
                "Perfil inicial creado al aprobar solicitud de afiliación.", reviewerEmail
        );

        Account managerAccount = new Account(UUID.randomUUID(), contactEmail,
                passwordEncoder.encode(UUID.randomUUID().toString()),
                Set.of("ADMIN_EMPRESA"), "PENDIENTE_ACTIVACION", false);
        managerAccount.setOrganizationId(orgId);
        accountRepository.saveAndFlush(managerAccount);

        jdbc.update(
                "UPDATE organization_requests SET status = 'APROBADA', approved_organization_id = ?, reviewed_by = ?, updated_at_utc = NOW() WHERE id = ?",
                orgId, reviewerEmail, requestId
        );
        audit.recordEvent(reviewerEmail, "APPROVE_COMPANY_REQUEST", "OrganizationRequest", requestId.toString(), "Empresa aprovisionada");
        return Map.of("requestId", requestId, "status", "APROBADA", "organizationId", orgId, "organizationCode", orgCode);
    }

    public Map<String, Object> exportOrganizationPortableSummary(UUID organizationId, String actorEmail) {
        Account actor = accountRepository.findByEmailIgnoreCase(actorEmail)
                .orElseThrow(() -> new AccessDeniedException("Cuenta no autorizada para exportar datos."));
        boolean isSuperAdmin = actor.getRoles().stream()
                .anyMatch(r -> "SUPER_ADMIN".equalsIgnoreCase(r));
        if (!isSuperAdmin) {
            UUID actorOrgId = actor.getOrganizationId() != null
                    ? actor.getOrganizationId()
                    : UUID.fromString("11111111-1111-4111-8111-111111111111");
            if (!actorOrgId.equals(organizationId)) {
                throw new AccessDeniedException("No tienes autorización para exportar datos de otra organización.");
            }
        }
        Map<String, Object> org = camelizeMap(jdbc.queryForMap(
                "SELECT id, code, name, tax_id, plan_tier, status, is_published, created_at FROM organizations WHERE id = ?",
                organizationId
        ));
        List<Map<String, Object>> branches = jdbc.queryForList(
                "SELECT id, code, name, city, district, address, status FROM branches WHERE organization_id = ? ORDER BY name ASC",
                organizationId
        ).stream().map(this::camelizeMap).toList();
        List<Map<String, Object>> ledger = listCommissionLedger(organizationId, null);

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportedAtUtc", LocalDate.now().toString());
        export.put("dataPortabilityGuarantee", "Exportación histórica disponible independientemente del estado comercial de la licencia.");
        export.put("organization", org);
        export.put("branches", branches);
        export.put("commissionLedgerCount", ledger.size());
        export.put("commissionLedger", ledger);
        return export;
    }

    private String normalizeInSet(String value, Set<String> validSet, String message) {
        String norm = value != null ? value.trim().toUpperCase(Locale.ROOT) : "";
        if (!validSet.contains(norm)) {
            throw new IllegalArgumentException(message);
        }
        return norm;
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
