package com.gymvip.service;

import com.gymvip.training.ClassReservationsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class ClassNoticeScopeIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ClassReservationsService classes;

    @Test
    void receptionSeesItsBranchAndTrainerOnlyAssignedClasses() {
        UUID company = company();
        UUID otherCompany = company();
        UUID main = branch(company);
        UUID second = branch(company);
        UUID foreign = branch(otherCompany);
        UUID reception = staff(company, main, "RECEPCION");
        UUID manager = staff(company, main, "GERENTE_GENERAL");
        UUID trainer = staff(company, main, "ENTRENADOR");

        UUID assigned = reservation(company, main, trainer);
        UUID colleague = reservation(company, main, staff(company, main, "ENTRENADOR"));
        UUID otherBranch = reservation(company, second, staff(company, second, "ENTRENADOR"));
        reservation(otherCompany, foreign, staff(otherCompany, foreign, "ENTRENADOR"));

        assertEquals(Set.of(assigned, colleague), visibleClasses(reception));
        assertEquals(Set.of(assigned), visibleClasses(trainer));
        assertEquals(Set.of(assigned, colleague, otherBranch), visibleClasses(manager));
    }

    private Set<UUID> visibleClasses(UUID account) {
        return classes.notices(account + "@example.test").stream()
                .map(ClassReservationsService.ClassNotice::classId).collect(Collectors.toSet());
    }

    private UUID company() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO organizations(id, code, name) VALUES (?, ?, 'Empresa de prueba')", id, id.toString());
        return id;
    }

    private UUID branch(UUID company) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO branches(id, organization_id, code, name) VALUES (?, ?, ?, 'Sede de prueba')",
                id, company, id.toString());
        return id;
    }

    private UUID staff(UUID company, UUID branch, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO accounts(id,email,password_hash,organization_id,primary_branch_id) VALUES (?,?,?,?,?)",
                id, id + "@example.test", "unused-in-this-test", company, branch);
        jdbc.update("INSERT INTO account_roles(account_id,role_id) VALUES (?,?)", id, role);
        return id;
    }

    private UUID reservation(UUID company, UUID branch, UUID trainer) {
        UUID course = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        UUID reservation = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO gym_classes(id,organization_id,branch_id,title,category,trainer_name,trainer_account_id,starts_at)
                VALUES (?,?,?,'Clase','Fuerza','Entrenador',?,CURRENT_TIMESTAMP)
                """, course, company, branch, trainer);
        jdbc.update("""
                INSERT INTO clients(id,first_name,last_name,birth_date,member_code,branch_id)
                VALUES (?,'Socio','Prueba',DATE '1990-01-01',?,?)
                """, client, "QA-" + client.toString().substring(0, 8), branch);
        jdbc.update("INSERT INTO class_reservations(id,class_id,client_id) VALUES (?,?,?)", reservation, course, client);
        jdbc.update("""
                INSERT INTO class_reservation_events(id,reservation_id,event_type,title,message)
                VALUES (?,?,'RESERVADA','Reserva confirmada','Aviso de prueba')
                """, UUID.randomUUID(), reservation);
        return course;
    }
}
