package com.gymvip.service;

import com.gymvip.dto.request.UpdateClientRequest;
import com.gymvip.dto.response.*;
import com.gymvip.entity.Document;
import com.gymvip.exception.*;
import com.gymvip.mapper.ClientMapper;
import com.gymvip.repository.ClientRepository;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class ClientEditingService {
    private final ClientRepository clients;
    private final ClientMapper mapper;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;
    public ClientEditingService(ClientRepository clients, ClientMapper mapper, JdbcTemplate jdbc,
                                AuditService audit, Clock clock) {
        this.clients=clients; this.mapper=mapper; this.jdbc=jdbc; this.audit=audit; this.clock=clock;
    }

    @Transactional
    public ClientDetailResponse update(UUID id, UpdateClientRequest request, String actor) {
        var client=clients.findById(id).orElseThrow(() -> new ClientNotFoundException("Cliente no encontrado."));
        if (client.getVersion()!=request.version()) throw new ConflictException("La ficha cambió. Recargue y revise su edición.");
        Document doc=client.getDocuments().stream().filter(Document::isPrimary).findFirst()
                .orElseThrow(() -> new ConflictException("El cliente no tiene documento principal. Requiere revisión."));
        String country=request.countryCode().trim().toUpperCase(Locale.ROOT);
        String type=request.documentType().trim().toUpperCase(Locale.ROOT);
        String number=request.documentNumber().trim().toUpperCase(Locale.ROOT);
        if (country.equals("PE") && type.equals("DNI") && !number.matches("[0-9]{8}"))
            throw new ClientValidationException("El DNI peruano debe contener exactamente 8 dígitos.");
        if (jdbc.queryForObject("SELECT count(*) FROM documents WHERE country_code=? AND document_type=? AND document_number=? AND id<>?",
                Long.class,country,type,number,doc.getId())>0)
            throw new DocumentDuplicateException("El documento ya pertenece a otro cliente.");
        var before=new LinkedHashMap<String,String>();
        before.put("firstName",client.getFirstName()); before.put("lastName",client.getLastName());
        before.put("countryCode",doc.getCountryCode()); before.put("documentType",doc.getDocumentType());
        before.put("documentNumber",doc.getDocumentNumber()); before.put("phone",client.getPhone()); before.put("email",client.getEmail());
        var after=new LinkedHashMap<String,String>();
        after.put("firstName",request.firstName().trim()); after.put("lastName",request.lastName().trim());
        after.put("countryCode",country); after.put("documentType",type); after.put("documentNumber",number);
        after.put("phone",nullable(request.phone()));
        after.put("email",nullable(request.email())==null?null:request.email().trim().toLowerCase(Locale.ROOT));
        UUID change=UUID.randomUUID(); LocalDateTime at=LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);
        var fields=after.keySet().stream().filter(k -> !Objects.equals(before.get(k),after.get(k))).toList();
        if (fields.isEmpty()) return mapper.toDetailResponse(client);
        client.setFirstName(after.get("firstName")); client.setLastName(after.get("lastName"));
        if (!Objects.equals(client.getPhone(),after.get("phone"))) client.setPhoneVerified(false);
        if (!Objects.equals(client.getEmail(),after.get("email"))) client.setEmailVerified(false);
        client.setPhone(after.get("phone")); client.setEmail(after.get("email"));
        // Even a document-only edit under a fixed clock must dirty the versioned parent.
        client.setUpdatedAt(at.equals(client.getUpdatedAt()) ? at.plusNanos(1000) : at);
        doc.setCountryCode(country); doc.setDocumentType(type); doc.setDocumentNumber(number);
        clients.saveAndFlush(client); // optimistic version check and uniqueness before writing audit
        for (String field: fields) jdbc.update("INSERT INTO client_changes(id,change_id,client_id,actor_email,field_name,previous_value,new_value,changed_at_utc) VALUES (?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),change,id,actor,field,before.get(field),after.get(field),at);
        audit.recordEvent(actor,"UPDATE_CLIENT","Client",id.toString(),"Campos: "+String.join(", ",fields));
        return mapper.toDetailResponse(client);
    }
    private String nullable(String value) { return value==null || value.isBlank()?null:value.trim(); }

    @Transactional(readOnly=true)
    public Page<ClientChangeResponse> history(UUID id, int page, int size) {
        if (!clients.existsById(id)) throw new ClientNotFoundException("Cliente no encontrado.");
        if(page<0 || size<1 || size>100) throw new IllegalArgumentException("Paginación inválida.");
        var rows=jdbc.query("SELECT * FROM client_changes WHERE client_id=? ORDER BY changed_at_utc DESC,change_id,field_name LIMIT ? OFFSET ?",
                (r,n)->new ClientChangeResponse(r.getObject("change_id",UUID.class),id,r.getString("actor_email"),
                        r.getString("field_name"),r.getString("previous_value"),r.getString("new_value"),
                        r.getObject("changed_at_utc",LocalDateTime.class)),id,size,(long)page*size);
        return new PageImpl<>(rows,PageRequest.of(page,size),jdbc.queryForObject("SELECT count(*) FROM client_changes WHERE client_id=?",Long.class,id));
    }
}
