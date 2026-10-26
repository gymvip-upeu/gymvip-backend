package com.gymvip.repository;

import com.gymvip.util.CoveragePolicy.Period;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.*;

@Repository
public class CoverageRepository {
    public record ClientRow(UUID id,String code,String name,String document,String status,String contractStatus) {}
    public record PaidRow(UUID clientId,Period period) {}
    private final JdbcTemplate jdbc;
    public CoverageRepository(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public List<ClientRow> clients(UUID id,String query) {
        return jdbc.query("""
            SELECT c.id,c.member_code,c.first_name||' '||c.last_name AS name,c.status,m.status AS contract_status,
              (SELECT d.document_type||' '||d.document_number FROM documents d WHERE d.client_id=c.id AND d.is_primary ORDER BY d.id LIMIT 1) AS document
            FROM clients c LEFT JOIN memberships m ON m.client_id=c.id AND m.service_code='GYM'
            WHERE (?::uuid IS NULL OR c.id=?::uuid) AND
              (?='' OR position(lower(?) in lower(c.first_name||' '||c.last_name||' '||coalesce(c.member_code,'')))>0
                OR EXISTS (SELECT 1 FROM documents d WHERE d.client_id=c.id AND position(lower(?) in lower(d.document_number))>0))
            ORDER BY c.last_name,c.first_name,c.id
            """,(r,n)->new ClientRow(r.getObject("id",UUID.class),r.getString("member_code"),r.getString("name"),
                r.getString("document"),r.getString("status"),r.getString("contract_status")),id,id,query,query,query);
    }
    public List<PaidRow> paid(UUID id) {
        return jdbc.query("""
            SELECT m.client_id,v.plan_id,v.name,p.start_date,p.end_date
            FROM memberships m JOIN membership_periods p ON p.membership_id=m.id
            JOIN plan_versions v ON v.id=p.plan_version_id
            WHERE m.service_code='GYM' AND (?::uuid IS NULL OR m.client_id=?::uuid)
              AND EXISTS (SELECT 1 FROM payment_records r WHERE r.period_id=p.id AND r.membership_id=m.id AND r.amount_pen=v.price_pen AND r.currency='PEN')
            ORDER BY p.start_date,p.end_date,p.id
            """,(r,n)->new PaidRow(r.getObject("client_id",UUID.class),new Period(r.getObject("plan_id",UUID.class),
                r.getString("name"),r.getObject("start_date",LocalDate.class),r.getObject("end_date",LocalDate.class))),id,id);
    }
}
