package com.gymvip.repository;

import com.gymvip.dto.faq.FaqDtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public class FaqRepository {
    private final JdbcTemplate jdbc;

    public FaqRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<FaqDtos.Record> findAll() {
        return jdbc.query("""
            SELECT id, question, answer, category, proposal_question, proposal_answer,
                   proposal_category, is_active, revision
            FROM gym_faqs ORDER BY display_order, created_at_utc, id
            """, (row, index) -> new FaqDtos.Record(
                row.getObject("id", UUID.class),
                new FaqDtos.Content(row.getString("question"), row.getString("answer"), row.getString("category")),
                row.getString("proposal_question") == null ? null : new FaqDtos.Content(
                    row.getString("proposal_question"), row.getString("proposal_answer"), row.getString("proposal_category")),
                row.getBoolean("is_active"), row.getInt("revision")));
    }

    public void lockForWrite() {
        // Serializa escrituras del catálogo pequeño para validar duplicados entre
        // contenido publicado y propuestas de registros distintos en la misma transacción.
        jdbc.execute("LOCK TABLE gym_faqs IN SHARE ROW EXCLUSIVE MODE");
    }

    public FaqDtos.Record create(FaqDtos.Content content) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO gym_faqs (id, question, answer, category, display_order, is_active)
            VALUES (?, ?, ?, ?, COALESCE((SELECT MAX(display_order) FROM gym_faqs), 0) + 1, false)
            """, id, content.question(), content.answer(), content.category());
        return new FaqDtos.Record(id, content, null, false, 1);
    }

    public FaqDtos.Record save(FaqDtos.Record previous, FaqDtos.Content content,
                               FaqDtos.Content proposal, boolean active) {
        jdbc.update("""
            UPDATE gym_faqs SET question=?, answer=?, category=?, proposal_question=?,
                proposal_answer=?, proposal_category=?, is_active=?, revision=revision+1,
                updated_at_utc=CURRENT_TIMESTAMP WHERE id=?
            """, content.question(), content.answer(), content.category(),
                proposal == null ? null : proposal.question(),
                proposal == null ? null : proposal.answer(),
                proposal == null ? null : proposal.category(), active, previous.id());
        return new FaqDtos.Record(previous.id(), content, proposal, active, previous.revision() + 1);
    }
}
