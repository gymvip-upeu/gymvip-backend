package com.gymvip.repository;

import com.gymvip.dto.response.MembershipSaleResponse;
import com.gymvip.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** PostgreSQL persistence for contracts, immutable periods and receipts. */
@Repository
public class MembershipRepository {
    private final JdbcTemplate jdbc;
    public MembershipRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Contract(UUID id, LocalDate end, String status) {}
    public record PlanSnapshot(UUID id, String name, String benefits, int duration, BigDecimal price) {}
    public record SavedOperation(String hash, String response) {}

    public UUID actor(String email) {
        return jdbc.query("SELECT id FROM accounts WHERE lower(email)=lower(?) AND status='ACTIVO'",
                (rs, n) -> rs.getObject(1, UUID.class), email).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador activo no encontrado."));
    }
    public void lockOperation(UUID actor, String key) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, actor + ":" + key);
    }
    public Optional<SavedOperation> operation(UUID actor, String key) {
        return jdbc.query("SELECT request_hash,response_json FROM membership_operations WHERE actor_id=? AND operation_key=?",
                (rs, n) -> new SavedOperation(rs.getString(1), rs.getString(2)), actor, key).stream().findFirst();
    }
    public void saveOperation(UUID actor, String key, String hash, String json) {
        jdbc.update("INSERT INTO membership_operations(actor_id,operation_key,request_hash,response_json) VALUES (?,?,?,?)",
                actor, key, hash, json);
    }
    public String clientStatus(UUID client, boolean lock) {
        return jdbc.query("SELECT status FROM clients WHERE id=?" + (lock ? " FOR UPDATE" : ""),
                (rs, n) -> rs.getString(1), client).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado."));
    }
    public Optional<Contract> contract(UUID client) {
        return jdbc.query("SELECT id,end_date,status FROM memberships WHERE client_id=? AND service_code='GYM'",
                (rs, n) -> new Contract(rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class), rs.getString(3)),
                client).stream().findFirst();
    }
    public PlanSnapshot plan(UUID planId, boolean lock) {
        var plans = jdbc.query("SELECT name,benefits,duration_days,price_pen,status FROM plans WHERE id=?" +
                        (lock ? " FOR SHARE" : ""), (rs, n) -> new Object[]{rs.getString(1), rs.getString(2),
                        rs.getInt(3), rs.getBigDecimal(4), rs.getString(5)}, planId);
        if (plans.isEmpty()) throw new ResourceNotFoundException("Plan no encontrado.");
        var plan = plans.getFirst();
        if (!"ACTIVO".equals(plan[4])) throw new com.gymvip.exception.InvalidPlanException("El plan está inactivo.");
        var snapshot = jdbc.query("SELECT id,name,benefits,duration_days,price_pen FROM plan_versions WHERE plan_id=? ORDER BY version_number DESC LIMIT 1",
                (rs, n) -> new PlanSnapshot(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getBigDecimal(5)),
                planId).stream().findFirst().orElseThrow(() -> new com.gymvip.exception.InvalidPlanException("El plan no tiene versión histórica."));
        if (!Objects.equals(plan[0], snapshot.name()) || !Objects.equals(plan[1], snapshot.benefits()) ||
                (int) plan[2] != snapshot.duration() || ((BigDecimal) plan[3]).compareTo(snapshot.price()) != 0) {
            throw new com.gymvip.exception.ConflictException("El plan y su versión histórica no coinciden. Solicite revisión del catálogo.");
        }
        return snapshot;
    }
    public void createContract(UUID id, UUID client, UUID version, LocalDate start, LocalDate end) {
        jdbc.update("INSERT INTO memberships(id,client_id,plan_version_id,start_date,end_date,status) VALUES (?,?,?,?,?,'VIGENTE')",
                id, client, version, start, end);
    }
    public void extendContract(UUID id, LocalDate end) {
        jdbc.update("UPDATE memberships SET end_date=?,status='VIGENTE' WHERE id=?", end, id);
    }
    public void addPeriod(UUID id, UUID contract, UUID version, LocalDate start, LocalDate end, LocalDateTime at, String policy) {
        jdbc.update("INSERT INTO membership_periods(id,membership_id,plan_version_id,start_date,end_date,created_at,policy_text) VALUES (?,?,?,?,?,?,?)",
                id, contract, version, start, end, at, policy);
    }
    public void addPayment(MembershipSaleResponse sale, UUID actor) {
        jdbc.update("INSERT INTO payment_records(id,membership_id,period_id,amount_pen,currency,payment_method,external_reference,registered_by_account_id,payment_timestamp_utc) VALUES (?,?,?,?,'PEN',?,?,?,?)",
                sale.paymentId(), sale.membershipId(), sale.periodId(), sale.amountPen(), sale.paymentMethod(), sale.reference(), actor, sale.paidAtUtc());
    }
    public List<MembershipSaleResponse> history(UUID client) {
        return jdbc.query("""
                SELECT m.id AS membership_id, p.id AS period_id, r.id AS payment_id, m.client_id,
                       v.id AS plan_version_id, v.name, v.benefits, p.policy_text, p.start_date, p.end_date,
                       r.amount_pen, r.currency, r.payment_method, r.external_reference,
                       a.email, r.payment_timestamp_utc
                FROM memberships m JOIN membership_periods p ON p.membership_id=m.id
                JOIN plan_versions v ON v.id=p.plan_version_id
                JOIN payment_records r ON r.period_id=p.id
                JOIN accounts a ON a.id=r.registered_by_account_id
                WHERE m.client_id=? ORDER BY p.start_date DESC, r.id
                """, (rs, n) -> mapSale(rs), client);
    }
    private MembershipSaleResponse mapSale(ResultSet r) throws SQLException {
        LocalDate end = r.getObject("end_date", LocalDate.class);
        UUID payment = r.getObject("payment_id", UUID.class);
        return new MembershipSaleResponse(r.getObject("membership_id", UUID.class), r.getObject("period_id", UUID.class),
                payment, r.getObject("client_id", UUID.class), r.getObject("plan_version_id", UUID.class),
                r.getString("name"), r.getString("benefits"), r.getString("policy_text"), r.getObject("start_date", LocalDate.class), end.minusDays(1), end,
                r.getBigDecimal("amount_pen"), r.getString("currency"), r.getString("payment_method"), r.getString("external_reference"),
                r.getString("email"), r.getObject("payment_timestamp_utc", LocalDateTime.class), "VIP-" + payment);
    }
}
