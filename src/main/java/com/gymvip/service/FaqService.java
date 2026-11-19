package com.gymvip.service;

import com.gymvip.dto.faq.FaqDtos;
import com.gymvip.exception.ConflictException;
import com.gymvip.exception.ResourceNotFoundException;
import com.gymvip.repository.FaqRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FaqService {
    private static final Set<String> CATEGORIES = Set.of(
        "Horarios", "Membresías", "Renovaciones", "Servicios", "Suplementos",
        "Pagos", "Historial", "Instalaciones", "Cuenta", "Catálogo");
    private final FaqRepository repository;
    private final AuditService audit;

    public FaqService(FaqRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<FaqDtos.Record> list() {
        return repository.findAll();
    }

    @Transactional
    public FaqDtos.Record create(FaqDtos.Content request, String actor) {
        FaqDtos.Content content = validate(request);
        repository.lockForWrite();
        checkDuplicate(content, null, repository.findAll());
        FaqDtos.Record result = repository.create(content);
        recordAudit(actor, "FAQ_CREATED", result);
        return result;
    }

    @Transactional
    public FaqDtos.Record edit(UUID id, FaqDtos.Update request, String actor) {
        FaqDtos.Content content = validate(request.content());
        repository.lockForWrite();
        List<FaqDtos.Record> records = repository.findAll();
        FaqDtos.Record current = current(records, id, request.revision());
        checkDuplicate(content, id, records);
        FaqDtos.Record result = current.active()
            ? repository.save(current, current.content(), content, true)
            : repository.save(current, content, null, false);
        recordAudit(actor, "FAQ_EDITED", result);
        return result;
    }

    @Transactional
    public FaqDtos.Record transition(UUID id, int revision, String action, String actor) {
        repository.lockForWrite();
        List<FaqDtos.Record> records = repository.findAll();
        FaqDtos.Record current = current(records, id, revision);
        FaqDtos.Content content = current.content();
        FaqDtos.Content proposal = current.proposal();
        boolean active;
        switch (action) {
            case "publish" -> {
                if (proposal == null) throw new ConflictException("La respuesta no tiene una propuesta pendiente.");
                content = proposal;
                proposal = null;
                active = true;
            }
            case "activate" -> active = true;
            case "deactivate" -> active = false;
            default -> throw new IllegalArgumentException("Acción de publicación inválida.");
        }
        checkDuplicate(content, id, records);
        FaqDtos.Record result = repository.save(current, content, proposal, active);
        recordAudit(actor, "FAQ_" + action.toUpperCase(Locale.ROOT), result);
        return result;
    }

    private FaqDtos.Record current(List<FaqDtos.Record> records, UUID id, int revision) {
        FaqDtos.Record current = records.stream().filter(row -> row.id().equals(id)).findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Respuesta frecuente no encontrada."));
        if (current.revision() != revision) {
            throw new ConflictException("La respuesta cambió en otra sesión. Actualiza el catálogo y vuelve a abrirla.");
        }
        return current;
    }

    private FaqDtos.Content validate(FaqDtos.Content content) {
        if (!CATEGORIES.contains(content.category()) || normalize(content.question()).isBlank()
                || content.answer().isBlank()) {
            throw new IllegalArgumentException("Completa la categoría, la pregunta y la respuesta.");
        }
        return new FaqDtos.Content(content.question().trim(), content.answer().trim(), content.category());
    }

    private void checkDuplicate(FaqDtos.Content content, UUID id, List<FaqDtos.Record> records) {
        String question = normalize(content.question());
        for (FaqDtos.Record record : records) {
            if (record.id().equals(id)) continue;
            if (normalize(record.content().question()).equals(question)
                    || record.proposal() != null && normalize(record.proposal().question()).equals(question)) {
                throw new ConflictException("Ya existe una pregunta equivalente. Busca el registro existente para editarlo.");
            }
        }
    }

    private String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private void recordAudit(String actor, String action, FaqDtos.Record record) {
        audit.recordEvent(actor, action, "GymFaq", record.id().toString(), "Revisión " + record.revision());
    }
}
