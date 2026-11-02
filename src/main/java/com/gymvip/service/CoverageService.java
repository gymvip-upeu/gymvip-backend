package com.gymvip.service;

import com.gymvip.dto.response.CoverageResponse;
import com.gymvip.exception.ClientNotFoundException;
import com.gymvip.repository.CoverageRepository;
import com.gymvip.util.CoveragePolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class CoverageService {
    private final CoverageRepository repository;
    private final Clock clock;
    private final int threshold;
    public CoverageService(CoverageRepository repository,Clock clock,@Value("${MEMBERSHIP_UPCOMING_DAYS:7}") int threshold) {
        if(threshold<1 || threshold>30) throw new IllegalArgumentException("Umbral de vencimiento inválido.");
        this.repository=repository;this.clock=clock;this.threshold=threshold;
    }
    public CoverageResponse verify(UUID id) {
        return calculate(id,"").stream().findFirst().orElseThrow(()->new ClientNotFoundException("Cliente no encontrado."));
    }
    public Page<CoverageResponse> list(String query,String state,UUID planId,LocalDate from,LocalDate to,int page,int size) {
        if(page<0 || size<1 || size>100) throw new IllegalArgumentException("Paginación inválida.");
        if(from!=null && to!=null && from.isAfter(to)) throw new IllegalArgumentException("El inicio del rango supera el fin.");
        var states=Set.of("TODOS","VIGENTE","POR_VENCER","VENCIDO","CANCELADA","PENDIENTE","SIN_MEMBRESIA","ALERTAS");
        if(!states.contains(state)) throw new IllegalArgumentException("Estado inválido.");
        var rows=calculate(null,query==null?"":query.trim()).stream()
                .filter(r->state.equals("TODOS") || (state.equals("ALERTAS") ? Set.of("POR_VENCER","VENCIDO").contains(r.state()):state.equals(r.state())))
                .filter(r->planId==null || planId.equals(r.planId()))
                .filter(r->from==null || r.lastIncludedDate()!=null && !r.lastIncludedDate().isBefore(from))
                .filter(r->to==null || r.lastIncludedDate()!=null && !r.lastIncludedDate().isAfter(to))
                .sorted(Comparator.comparing(CoverageResponse::endExclusive,Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CoverageResponse::clientId)).toList();
        int start=(int)Math.min((long)page*size,rows.size());
        return new PageImpl<>(rows.subList(start,Math.min(start+size,rows.size())),PageRequest.of(page,size),rows.size());
    }
    private List<CoverageResponse> calculate(UUID id,String query) {
        Clock snapshot=Clock.fixed(clock.instant(),ZoneOffset.UTC);
        var paid=repository.paid(id).stream().collect(Collectors.groupingBy(CoverageRepository.PaidRow::clientId,
                Collectors.mapping(CoverageRepository.PaidRow::period,Collectors.toList())));
        return repository.clients(id,query).stream().map(c->{
            var result=CoveragePolicy.evaluate(paid.getOrDefault(c.id(),List.of()),c.contractStatus(),c.status(),snapshot,threshold);
            return new CoverageResponse(c.id(),c.code(),c.name(),c.document(),c.status(),c.contractStatus(),result.state(),
                    result.allowed(),result.reason(),result.planId(),result.planName(),result.start(),
                    result.end()==null?null:result.end().minusDays(1),result.end(),result.daysRemaining(),snapshot.instant(),
                    CoveragePolicy.ZONE.getId(),threshold);
        }).toList();
    }
}
