package com.gymvip.catalog;

import com.gymvip.security.TenantScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

public record MemberSupplementStock(UUID branchId, boolean legacyInventory) {
    public static MemberSupplementStock forAccount(JdbcTemplate jdbc, String email) {
        return jdbc.query("""
                SELECT b.id, COALESCE(a.organization_id, ?) = ? AS legacy
                FROM accounts a
                LEFT JOIN clients c ON c.account_id = a.id AND c.status = 'ACTIVO'
                LEFT JOIN branches b ON b.id = COALESCE(c.branch_id, a.primary_branch_id,
                    CASE WHEN COALESCE(a.organization_id, ?) = ? THEN CAST(? AS uuid) END)
                    AND b.organization_id = COALESCE(a.organization_id, ?) AND b.status = 'ACTIVO'
                WHERE LOWER(a.email) = LOWER(?)
                """, (rs, row) -> new MemberSupplementStock(rs.getObject("id", UUID.class), rs.getBoolean("legacy")),
                TenantScopeResolver.DEFAULT_ORG_ID, TenantScopeResolver.DEFAULT_ORG_ID,
                TenantScopeResolver.DEFAULT_ORG_ID, TenantScopeResolver.DEFAULT_ORG_ID,
                TenantScopeResolver.DEFAULT_BRANCH_ID, TenantScopeResolver.DEFAULT_ORG_ID, email)
                .stream().findFirst().orElse(new MemberSupplementStock(null, false));
    }

    public boolean includes(UUID productBranch) {
        return branchId != null && (productBranch == null ? legacyInventory : branchId.equals(productBranch));
    }
}
