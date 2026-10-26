package com.gymvip;

import com.gymvip.util.CoveragePolicy;
import com.gymvip.util.CoveragePolicy.Period;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CoveragePolicyTest {
    final UUID plan=UUID.randomUUID();
    Period period(String start,String end) {return new Period(plan,"Mensual",LocalDate.parse(start),LocalDate.parse(end));}
    CoveragePolicy.Result evaluate(List<Period> periods,String instant) {
        return CoveragePolicy.evaluate(periods,"VIGENTE","ACTIVO",Clock.fixed(Instant.parse(instant),ZoneOffset.UTC),7);
    }
    @Test void exactLimaMidnightEndsCoverageWithoutGrace() {
        var paid=List.of(period("2026-10-26","2026-11-25"));
        assertTrue(evaluate(paid,"2026-11-25T04:59:59Z").allowed());
        assertEquals("VENCIDO",evaluate(paid,"2026-11-25T05:00:00Z").state());
        assertFalse(evaluate(paid,"2026-11-25T05:00:01Z").allowed());
    }
    @Test void upcomingUsesExactlyLastSevenValidDays() {
        var paid=List.of(period("2026-10-26","2026-11-25"));
        assertEquals("VIGENTE",evaluate(paid,"2026-11-17T17:00:00Z").state());
        assertEquals("POR_VENCER",evaluate(paid,"2026-11-18T17:00:00Z").state());
    }
    @Test void contiguousRenewalsExtendFinalCoverage() {
        var result=evaluate(List.of(period("2026-10-26","2026-11-25"),period("2026-11-25","2026-12-25")),"2026-11-24T17:00:00Z");
        assertEquals(LocalDate.of(2026,12,25),result.end()); assertEquals("VIGENTE",result.state());
    }
    @Test void lateRenewalDoesNotCoverGap() {
        var paid=List.of(period("2026-10-26","2026-11-25"),period("2026-12-10","2027-01-09"));
        assertFalse(evaluate(paid,"2026-12-01T17:00:00Z").allowed());
        assertTrue(evaluate(paid,"2026-12-10T05:00:00Z").allowed());
    }
    @Test void cancelledInactiveFutureAndUnpaidNeverAuthorize() {
        var paid=List.of(period("2026-10-26","2026-11-25"));
        Clock clock=Clock.fixed(Instant.parse("2026-10-26T17:00:00Z"),ZoneOffset.UTC);
        assertFalse(CoveragePolicy.evaluate(paid,"CANCELADA","ACTIVO",clock,7).allowed());
        assertFalse(CoveragePolicy.evaluate(paid,"VIGENTE","INACTIVO",clock,7).allowed());
        assertTrue(CoveragePolicy.evaluate(paid,"VENCIDO","ACTIVO",clock,7).allowed());
        assertEquals("SIN_MEMBRESIA",evaluate(List.of(),"2026-10-26T17:00:00Z").state());
        assertEquals("PENDIENTE",evaluate(paid,"2026-10-25T17:00:00Z").state());
    }
    @Test void thirtyDaysWorkAcrossEveryMonthAndLeapYear() {
        for(int year:List.of(2024,2025,2026)) for(int month=1;month<=12;month++) {
            LocalDate start=LocalDate.of(year,month,1),end=start.plusDays(30);
            var paid=List.of(new Period(plan,"Mensual",start,end));
            assertTrue(evaluate(paid,end.atStartOfDay(CoveragePolicy.ZONE).minusNanos(1).toInstant().toString()).allowed());
            assertFalse(evaluate(paid,end.atStartOfDay(CoveragePolicy.ZONE).toInstant().toString()).allowed());
        }
    }
}
