package com.gymvip.util;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Single read-time rule for HU-09/11/12. No scheduler or browser clock required. */
public final class CoveragePolicy {
    public static final ZoneId ZONE=MembershipDates.ZONE;
    public record Period(UUID planId,String planName,LocalDate start,LocalDate end) {}
    public record Result(String state, boolean allowed, LocalDate start, LocalDate end,
                         UUID planId,String planName,Long daysRemaining,String reason) {}
    private CoveragePolicy() {}
    public static Result evaluate(List<Period> paid, String contractStatus, String clientStatus, Clock clock, int threshold) {
        LocalDate today=LocalDate.now(clock.withZone(ZONE));
        var sorted=paid.stream().sorted(Comparator.comparing(Period::start).thenComparing(Period::end)).toList();
        var blocks=new ArrayList<Period>();
        for(var p:sorted) {
            if (!p.end().isAfter(p.start())) throw new IllegalArgumentException("Periodo inválido.");
            if (!blocks.isEmpty() && !p.start().isAfter(blocks.getLast().end())) {
                var previous=blocks.removeLast();
                boolean extendsEnd=p.end().isAfter(previous.end());
                blocks.add(new Period(extendsEnd?p.planId():previous.planId(),extendsEnd?p.planName():previous.planName(),
                        previous.start(),extendsEnd?p.end():previous.end()));
            } else blocks.add(p);
        }
        var current=blocks.stream().filter(p->!today.isBefore(p.start()) && today.isBefore(p.end())).findFirst();
        var selected=current.orElseGet(()->blocks.stream().filter(p->!p.end().isAfter(today)).reduce((a,b)->b)
                .orElse(blocks.isEmpty()?null:blocks.getFirst()));
        String state; String reason; boolean allowed=false;
        if ("CANCELADA".equals(contractStatus)) {state="CANCELADA";reason="Contrato cancelado.";}
        else if (selected==null) {state="SIN_MEMBRESIA";reason="Sin cobertura pagada confirmada. Requiere contratación.";}
        else if (current.isEmpty()) {
            state=today.isBefore(selected.start())?"PENDIENTE":"VENCIDO";
            reason=state.equals("PENDIENTE")?"La cobertura aún no comienza.":"Sin cobertura pagada para hoy. Requiere renovación.";
        } else {
            state=ChronoUnit.DAYS.between(today,selected.end())<=threshold?"POR_VENCER":"VIGENTE";
            allowed="ACTIVO".equals(clientStatus) && contractStatus!=null && Set.of("VIGENTE","POR_VENCER","VENCIDO").contains(contractStatus);
            reason=allowed?"Cobertura pagada confirmada para hoy.":"Cliente o contrato no habilitado.";
        }
        return new Result(state,allowed,selected==null?null:selected.start(),selected==null?null:selected.end(),
                selected==null?null:selected.planId(),selected==null?null:selected.planName(),
                selected==null?null:ChronoUnit.DAYS.between(today,selected.end()),reason);
    }
}
