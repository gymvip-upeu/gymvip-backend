package com.gymvip.audit.infrastructure.persistence.adapter;

import com.gymvip.audit.application.port.out.AuditPersistencePort;
import com.gymvip.audit.domain.model.AuditEvent;
import com.gymvip.audit.infrastructure.persistence.entity.AuditEventJpaEntity;
import com.gymvip.audit.infrastructure.persistence.repository.SpringDataAuditEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class AuditPersistenceAdapter implements AuditPersistencePort {

    private final SpringDataAuditEventRepository repository;

    public AuditPersistenceAdapter(SpringDataAuditEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public AuditEvent save(AuditEvent event) {
        AuditEventJpaEntity entity = new AuditEventJpaEntity(
                event.getId(),
                event.getActorEmail(),
                event.getAction(),
                event.getEntityName(),
                event.getEntityId(),
                event.getDetails(),
                event.getTimestampUtc()
        );
        AuditEventJpaEntity saved = repository.save(entity);
        return new AuditEvent(
                saved.getId(),
                saved.getActorEmail(),
                saved.getAction(),
                saved.getEntityName(),
                saved.getEntityId(),
                saved.getDetails(),
                saved.getTimestampUtc()
        );
    }

    @Override
    public List<AuditEvent> findRecent(int limit) {
        return repository.findAll(PageRequest.of(0, limit, Sort.by("timestampUtc").descending()))
                .stream()
                .map(e -> new AuditEvent(
                        e.getId(),
                        e.getActorEmail(),
                        e.getAction(),
                        e.getEntityName(),
                        e.getEntityId(),
                        e.getDetails(),
                        e.getTimestampUtc()
                ))
                .collect(Collectors.toList());
    }
}
